package com.ivy.legacy.notification

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.base.legacy.stringRes
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.domain.AppStarter
import com.ivy.domain.usecase.billreminder.BillReminderDates
import com.ivy.domain.usecase.billreminder.BillReminderInbox
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.IvyNotificationId
import com.ivy.wallet.android.notification.NotificationResult
import com.ivy.wallet.android.notification.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth

/**
 * Fires one bill reminder: fabricates a pending bill, tells the owner, re-arms
 * next month. Nothing else.
 *
 * ## What this worker deliberately does not do
 *
 * It does not create a transaction. It does not call `PlannedPaymentsGenerator`,
 * which eagerly writes up to 72 real `transactions` rows spanning three years and
 * re-runs on every rule save - the behaviour the owner explicitly rejected. A
 * reminder produces exactly one row in `pending_bills`, which is outside the
 * ledger and therefore outside every balance, report and export.
 *
 * It does not depend on the owner having opened anything. Constraint 3 is that
 * delivery must not be confused with action: the notification is a consequence of
 * *firing*, posted here, and the pending row's existence is not evidence that
 * anyone was told. Equally, the row existing is not evidence that anyone acted,
 * so nothing here waits on it.
 *
 * ## Idempotency
 *
 * Two independent layers, because they fail differently:
 *
 *  - The **enqueue** is unique per rule and uses REPLACE, so a re-schedule cannot
 *    stack two requests for one bill (`BillReminderScheduler`).
 *  - The **fabrication** is idempotent at the database, on a derived primary key.
 *    A WorkManager retry, a reboot mid-write or a re-run all recompute the same
 *    occurrence key and are refused by SQLite. See `BillReminderInbox`.
 *
 * ## Failure handling
 *
 * Returns [Result.retry] when the database or WorkManager is temporarily
 * unavailable, so WorkManager's own backoff re-runs it and the re-run is harmless
 * thanks to the key above. A rule that has since been deleted returns
 * [Result.success] - there is nothing to retry, and retrying forever for a rule
 * that no longer exists would be a wakeup leak.
 */
@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val billReminderInbox: BillReminderInbox,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
    private val timeProvider: TimeProvider,
    private val scheduler: BillReminderScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ruleIdString = inputData.getString(KEY_RULE_ID)
        val dayOfMonth = inputData.getInt(KEY_DUE_DAY_OF_MONTH, -1)

        if (ruleIdString == null || !BillReminderDates.isValidDayOfMonth(dayOfMonth)) {
            // Malformed input will not become well-formed on a retry.
            Timber.w("Bill reminder fired with bad input; dropping it.")
            return@withContext Result.success()
        }

        val ruleId = runCatching { java.util.UUID.fromString(ruleIdString) }.getOrNull()
        if (ruleId == null) {
            Timber.w("Bill reminder fired with an unparseable rule id; dropping it.")
            return@withContext Result.success()
        }

        val rule = plannedPaymentRuleDao.findById(ruleId)
        if (rule == null) {
            // The bill was deleted between arming and firing. Nothing to remind
            // about, and nothing to re-arm.
            Timber.i("Bill reminder fired for rule $ruleId, which no longer exists.")
            return@withContext Result.success()
        }

        // If the owner removed the day since this was armed, stop re-arming and
        // stay silent. The armed request outlived the setting that justified it.
        if (!BillReminderDates.isValidDayOfMonth(rule.dueDayOfMonth)) {
            Timber.i("Bill reminder fired for rule $ruleId, which no longer has a due day.")
            return@withContext Result.success()
        }

        val today = timeProvider.localDateNow()
        val dueOn = BillReminderDates.clampToMonth(YearMonth.from(today), dayOfMonth)

        val outcome = runCatching {
            billReminderInbox.fabricate(
                ruleId = ruleId,
                dueOn = dueOn,
                amount = rule.amount,
                type = rule.type,
                accountId = rule.accountId,
                title = rule.title,
                description = rule.description,
                zone = timeProvider.getZoneId(),
            )
        }.getOrElse { error ->
            // A database that is momentarily unavailable is worth retrying, and the
            // retry is safe because the occurrence key is derived rather than
            // generated.
            Timber.e(error, "Bill reminder for rule $ruleId could not be recorded.")
            return@withContext Result.retry()
        }

        when (outcome) {
            is BillReminderInbox.Result.Created -> notifyOwner(
                ruleId = ruleIdString,
                title = rule.title,
                amount = rule.amount,
                dueOn = dueOn,
            )

            // Already fabricated, paid or dismissed: no second notification. The
            // owner has already been told about this occurrence, or has already
            // said he does not want to hear about it.
            BillReminderInbox.Result.AlreadyHandled -> Timber.i(
                "Bill reminder for rule $ruleId on $dueOn was already handled; " +
                    "no second notification."
            )
        }

        // Re-arm next month from inside the worker. `nextOccurrence` is strictly
        // after now, so this lands on the following month rather than looping on
        // today's date - which is what makes a self-re-arming one-shot safe.
        //
        // The rule's CURRENT day is re-read rather than the one this request was
        // armed with: if the owner changed the bill's day between arming and
        // firing, the reminder should continue on the new day.
        val rearmDay = rule.dueDayOfMonth
        if (rearmDay != null) {
            runCatching { scheduler.schedule(ruleId, rearmDay) }
                .onFailure { error ->
                    Timber.e(error, "Could not re-arm the bill reminder for rule $ruleId.")
                    // WorkManager's own backoff will re-run, and the re-run is safe.
                    return@withContext Result.retry()
                }
        }

        Result.success()
    }

    private fun notifyOwner(
        ruleId: String,
        title: String?,
        amount: Double,
        dueOn: LocalDate,
    ) {
        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.BILL_REMINDER,
                priority = NotificationCompat.PRIORITY_HIGH,
            )
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: stringRes(R.string.bill_reminder))
            .setContentText(billReminderBody(amount, dueOn))
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    // Request code distinct from the daily reminder's, so the two
                    // features do not share a PendingIntent either. Same
                    // reasoning as IvyNotificationId: these replace each other.
                    IvyNotificationId.BILL_REMINDER_BASE,
                    appStarter.getRootIntent(),
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT
                            or PendingIntent.FLAG_IMMUTABLE,
                )
            )

        // The result is checked, not ignored, for the same reason the daily
        // reminder checks it: on API 33+ a user who declined POST_NOTIFICATIONS
        // would otherwise get silence indistinguishable from "no bill is due".
        when (notificationService.showNotification(notification, IvyNotificationId.billReminder(ruleId))) {
            NotificationResult.Posted -> Unit

            NotificationResult.BlockedByPermission -> Timber.i(
                "Bill reminder for rule $ruleId suppressed: the user has not allowed " +
                    "notifications. The pending bill is still recorded."
            )

            NotificationResult.Failed -> Timber.e(
                "Bill reminder for rule $ruleId could not be posted. The pending " +
                    "bill is still recorded."
            )
        }
    }

    /**
     * "Your <b> bill of <amount> is due." with the date spelled out.
     *
     * The pending bill exists in the database either way, so a suppressed or
     * failed notification never costs the owner the record - only the prompt. That
     * is what makes the two outcomes safe to log differently and recover from
     * separately.
     */
    /**
     * "Due 25 Jan 2027 - 500,000" so the owner can check the figure at a glance.
     *
     * The amount is included rather than just the date because the whole point of
     * the feature is that the amount is adjustable: he needs to see what the app
     * believes he owes in order to decide whether to correct it. A reminder that
     * only said "a bill is due" would give him nothing to disagree with.
     */
    private fun billReminderBody(amount: Double, dueOn: LocalDate): String =
        stringRes(
            R.string.bill_reminder_due,
            dueOn.toString(),
            formatAmount(amount),
        )

    /**
     * Formats the amount in the base currency's own convention.
     *
     * Grouping separators included: an unformatted five-digit figure is genuinely
     * harder to check at a glance than a grouped one, and checking the figure is
     * the owner's main reason for reading the notification.
     */
    private fun formatAmount(amount: Double): String =
        java.text.NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 2
        }.format(amount)

    companion object {
        const val KEY_RULE_ID = "rule_id"
        const val KEY_DUE_DAY_OF_MONTH = "due_day_of_month"
    }
}
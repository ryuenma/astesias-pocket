package com.ivy.legacy.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.domain.usecase.billreminder.BillReminderDates
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Enqueues one-shot work at the exact next occurrence of each bill's chosen day.
 *
 * ## Why not `PeriodicWorkRequestBuilder(24, TimeUnit.HOURS)`
 *
 * `TransactionReminderLogic` schedules the daily reminder that way and it is the
 * right tool there, because "every 24 hours" is what a daily reminder *is*. It is
 * the wrong tool for "the 25th of each month", for three independent reasons:
 *
 *  1. **A period is a duration, not a calendar position.** Months are 28, 29, 30
 *     or 31 days, so a 24-hour period cannot stay on a day-of-month: work that
 *     fired on the 25th fires on the 26th within two months. No interval length is
 *     correct in both February and March.
 *  2. **Periodic work is inexact.** WorkManager treats it as a *request* the
 *     system may batch and defer under Doze. The run time drifts, and a bill
 *     reminder that lands after midnight has landed on the wrong day.
 *  3. **It cannot re-anchor.** The existing worker returns `Result.success()` and
 *     re-enqueues nothing, so nothing anywhere in this codebase computes "the
 *     next occurrence". A month-aware reminder has to recompute that from the
 *     calendar after each run, which is what [BillReminderDates.nextOccurrence]
 *     exists for.
 *
 * So this enqueues a **one-shot** request per rule with an initial delay computed
 * from the calendar, and the worker re-arms the following month from inside
 * `doWork()`. One-shot work with an explicit delay is still subject to Doze
 * deferral - nothing in WorkManager is an alarm - but it is anchored to a real
 * instant, so a deferral delays this month's reminder rather than permanently
 * shifting the schedule onto the wrong day of the next one.
 *
 * ## Idempotency of the enqueue itself
 *
 * Each rule gets a unique work name derived from its id, enqueued with
 * [ExistingWorkPolicy.REPLACE]. Re-scheduling the same rule therefore replaces its
 * pending request instead of adding a second one, so calling this repeatedly - on
 * every app start, and again on every rule save - cannot stack up duplicate
 * reminders for one bill.
 */
class BillReminderScheduler @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val timeProvider: TimeProvider,
    private val timeConverter: TimeConverter,
) {

    /**
     * Re-arms a reminder for every rule that has a day of the month set.
     *
     * Cheap and idempotent, so it is safe to call on every app start and after
     * every rule save. Rules with no day set are skipped: they have no reminder,
     * which is what a null `dueDayOfMonth` has meant since the column was added.
     */
    suspend fun scheduleAll() {
        val rules = plannedPaymentRuleDao.findAllWithBillReminder()
        Timber.i("Re-arming ${rules.size} bill reminder(s).")

        rules.forEach { rule ->
            // A day outside 1..31 is treated as "no reminder" rather than clamped,
            // so a corrupt value cannot turn into a reminder on some arbitrary
            // day the owner never chose.
            val day = rule.dueDayOfMonth
            if (!BillReminderDates.isValidDayOfMonth(day)) {
                Timber.w(
                    "Rule ${rule.id} has an out-of-range dueDayOfMonth=$day; " +
                        "no reminder scheduled."
                )
                return@forEach
            }
            schedule(rule.id, day!!)
        }
    }

    /**
     * Arms (or re-arms) the reminder for one rule at [dayOfMonth].
     *
     * [ExistingWorkPolicy.REPLACE] rather than KEEP: a rule whose day was just
     * changed must move to the new day, and KEEP would leave the old request
     * armed and fire on the day the owner no longer wants.
     */
    fun schedule(ruleId: UUID, dayOfMonth: Int) {
        val now = timeProvider.utcNow()
        val next = BillReminderDates.nextOccurrence(
            dayOfMonth = dayOfMonth,
            now = with(timeConverter) { now.toLocalDateTime() },
            zone = timeProvider.getZoneId(),
        )

        val delayMillis = (next.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<BillReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    BillReminderWorker.KEY_RULE_ID to ruleId.toString(),
                    BillReminderWorker.KEY_DUE_DAY_OF_MONTH to dayOfMonth,
                )
            )
            .addTag(BILL_REMINDER_TAG)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            uniqueWorkName(ruleId),
            ExistingWorkPolicy.REPLACE,
            request,
        )

        Timber.i("Bill reminder for rule $ruleId armed for $next (in ${delayMillis}ms).")
    }

    /**
     * Cancels a rule's pending reminder.
     *
     * Called when a rule loses its day or is deleted. Without it, deleting a bill
     * would leave an armed request that fires for a rule that no longer exists.
     */
    fun cancel(ruleId: UUID) {
        WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWorkName(ruleId))
    }

    private fun uniqueWorkName(ruleId: UUID): String = "bill_reminder_$ruleId"

    companion object {
        /** Tag on every bill-reminder request, for bulk cancellation and debugging. */
        const val BILL_REMINDER_TAG = "bill_reminder"
    }
}
package com.ivy.wallet.domain.deprecated.logic.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ivy.base.legacy.SharedPrefs
import com.ivy.legacy.utils.timeNowLocal
import com.ivy.legacy.utils.toEpochSeconds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@Deprecated("Use FP style, look into `domain.fp` package")
class TransactionReminderLogic @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val sharedPrefs: SharedPrefs,
) {
    companion object {
        private const val UNIQUE_WORK_NAME_V1 = "transaction_reminder_work"
        private const val UNIQUE_WORK_NAME_V2 = "transaction_reminder_work_v2"
        private const val UNIQUE_WORK_NAME_TEST = "transaction_reminder_work_test"
    }

    fun testNow() {
        val workBuilder = PeriodicWorkRequestBuilder<TransactionReminderWorker>(5, TimeUnit.MINUTES)

        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME_TEST,
                ExistingPeriodicWorkPolicy.REPLACE,
                workBuilder.build()
            )
    }

    fun scheduleReminder() {
        if (!fetchShowNotifications()) {
            return
        }

        val timeNowLocal = timeNowLocal()
        val today8PM = timeNowLocal()
            .withHour(20)
            .withMinute(0)

        val initialDelaySeconds = if (today8PM.isAfter(timeNowLocal)) {
            // 8 PM is in the future, we can start reminder today
            today8PM.toEpochSeconds() - timeNowLocal.toEpochSeconds()
        } else {
            // 8 PM has passed, we'll start reminding from tomorrow
            today8PM.plusDays(1).toEpochSeconds() - timeNowLocal.toEpochSeconds()
        }

        val workBuilder = PeriodicWorkRequestBuilder<TransactionReminderWorker>(24, TimeUnit.HOURS)
        if (initialDelaySeconds > 0) {
            workBuilder.setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
        }

        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME_V2,
                ExistingPeriodicWorkPolicy.KEEP,
                workBuilder.build()
            )
    }

    /**
     * Stops the daily reminder for good.
     *
     * scheduleReminder() is deliberately a no-op when the pref is off, which is
     * correct for "don't add another one" and wrong for "take the existing one
     * away". Without this, switching notifications off left the 24-hour
     * TransactionReminderWorker enqueued: it kept waking up every day, read the
     * pref, and skipped the notification - so the setting looked inert and the
     * app still held a wakeup alarm the user had switched off.
     */
    fun cancelReminder() {
        WorkManager
            .getInstance(appContext)
            .cancelUniqueWork(UNIQUE_WORK_NAME_V2)
    }

    private fun fetchShowNotifications(): Boolean =
        sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)
}

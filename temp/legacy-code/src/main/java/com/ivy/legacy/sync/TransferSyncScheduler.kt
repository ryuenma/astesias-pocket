package com.ivy.legacy.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ivy.data.sync.SyncConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules or cancels the 15-minute periodic [TransferSyncWorker] based on
 * whether PocketBase sync is configured on this device.
 */
class TransferSyncScheduler @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val syncConfig: SyncConfig,
) {
    fun scheduleIfConfigured() {
        if (!syncConfig.isConfigured) {
            cancel()
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<TransferSyncWorker>(
            REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Timber.i("TransferSyncWorker armed (every ${REPEAT_INTERVAL_MINUTES}m on network).")
    }

    fun cancel() {
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "astesia_transfer_sync"
        const val WORK_TAG = "astesia_transfer_sync"
        private const val REPEAT_INTERVAL_MINUTES = 15L
    }
}

package com.ivy.legacy.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.domain.usecase.transfer.TransferSyncRunner
import com.ivy.legacy.notification.TransferNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic background worker that reconciles the account directory, drains the
 * outbound transfer outbox, and pulls incoming transfer events and answers.
 *
 * Uses [EntryPointAccessors.fromApplication] with a standard two-argument
 * `(Context, WorkerParameters)` constructor rather than `@HiltWorker` so
 * WorkManager's default factory can instantiate it without `androidx.hilt:hilt-compiler`.
 */
class TransferSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun transferSyncRunner(): TransferSyncRunner
        fun transferNotifier(): TransferNotifier
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java,
        )
        val report = entryPoint.transferSyncRunner().runOnce()
        if (report.newTransfers > 0) {
            // Only what this pass actually brought in. A count of zero covers both
            // "nothing arrived" and "the pull failed", and neither is worth a
            // notification - the first is not news and the second would be wrong.
            entryPoint.transferNotifier().notifyIncoming(report.newTransfers)
        }
        Result.success()
    }
}

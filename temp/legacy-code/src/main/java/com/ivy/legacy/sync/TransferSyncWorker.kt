package com.ivy.legacy.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.domain.usecase.transfer.TransferSyncRunner
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
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java,
        )
        entryPoint.transferSyncRunner().runOnce()
        Result.success()
    }
}

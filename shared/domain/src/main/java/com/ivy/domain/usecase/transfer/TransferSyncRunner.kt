package com.ivy.domain.usecase.transfer

import com.ivy.data.sync.DirectorySyncRepository
import com.ivy.data.sync.SyncConfig
import com.ivy.data.sync.TransferOutboxRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one full cross-profile sync pass: directory publish + refresh, outbox
 * drain, incoming transfer events pull, and counterpart confirmations pull.
 *
 * Extracted from the Android `CoroutineWorker` so the ordering and error
 * isolation of the 5 steps are unit-testable on the JVM, and so app startup
 * (`MainViewModel`) and periodic background sync (`TransferSyncWorker`) run the
 * exact same sequence.
 */
@Singleton
class TransferSyncRunner @Inject constructor(
    private val config: SyncConfig,
    private val directorySync: DirectorySyncRepository,
    private val outbox: TransferOutboxRepository,
    private val eventSync: TransferEventSync,
) {
    suspend fun runOnce(): Boolean {
        if (!config.isConfigured) {
            Timber.i("Transfer sync skipped: sync is not configured.")
            return false
        }

        runCatching { directorySync.publishLocalAccounts() }
            .onFailure { Timber.e(it, "Transfer sync: publishLocalAccounts failed") }

        runCatching { directorySync.refresh() }
            .onFailure { Timber.e(it, "Transfer sync: directory refresh failed") }

        runCatching { outbox.drain() }
            .onFailure { Timber.e(it, "Transfer sync: outbox drain failed") }

        runCatching { eventSync.pullEvents() }
            .onFailure { Timber.e(it, "Transfer sync: pullEvents failed") }

        runCatching { eventSync.pullConfirmations() }
            .onFailure { Timber.e(it, "Transfer sync: pullConfirmations failed") }

        return true
    }
}

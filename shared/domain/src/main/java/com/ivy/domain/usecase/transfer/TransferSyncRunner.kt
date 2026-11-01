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
    suspend fun runOnce(): SyncReport {
        if (!config.isConfigured) {
            Timber.i("Transfer sync skipped: sync is not configured.")
            return SyncReport(ran = false)
        }

        runCatching { directorySync.publishLocalAccounts() }
            .onFailure { Timber.e(it, "Transfer sync: publishLocalAccounts failed") }

        runCatching { directorySync.refresh() }
            .onFailure { Timber.e(it, "Transfer sync: directory refresh failed") }

        runCatching { outbox.drain() }
            .onFailure { Timber.e(it, "Transfer sync: outbox drain failed") }

        // The results are kept rather than dropped: a caller that cannot tell "the
        // pull brought nothing" from "the pull brought three transfers" cannot
        // notify anyone, which is exactly how an incoming transfer sat unannounced
        // until the owner opened the app on a hunch.
        val events = runCatching { eventSync.pullEvents() }
            .onFailure { Timber.e(it, "Transfer sync: pullEvents failed") }
            .getOrNull()

        val answers = runCatching { eventSync.pullConfirmations() }
            .onFailure { Timber.e(it, "Transfer sync: pullConfirmations failed") }
            .getOrNull()

        return SyncReport(
            ran = true,
            newTransfers = (events as? TransferEventSync.PullResult.Pulled)?.newItems ?: 0,
            newAnswers = (answers as? TransferConfirmationRepository.AnswerPullResult.Pulled)
                ?.recorded ?: 0,
        )
    }
}

/**
 * What one sync pass did.
 *
 * [ran] is false only for a device that has no server; the counts are what arrived,
 * and they are zero for a pass that failed as well as for a quiet one. That is
 * deliberate: a failure is logged where it happens, and nothing may be notified
 * about a pass that could not read the server.
 */
data class SyncReport(
    val ran: Boolean,
    val newTransfers: Int = 0,
    val newAnswers: Int = 0,
)

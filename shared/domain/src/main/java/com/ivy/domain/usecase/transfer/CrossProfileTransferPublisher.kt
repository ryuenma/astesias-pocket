package com.ivy.domain.usecase.transfer

import com.ivy.data.sync.TransferOutboxRepository
import com.ivy.data.sync.orFallback
import com.ivy.data.sync.SyncConfig
import timber.log.Timber
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the server about a transfer this device has just recorded.
 *
 * Called immediately AFTER the owner's leg is in `transactions`, never before
 * and never instead. That ordering is the whole design: the ledger is the truth
 * and the network is a notification about it. See
 * [com.ivy.data.db.entity.TransferOutboxEntity] for why the queue is a table of
 * its own.
 *
 * ## What this guarantees
 *
 * 1. Recording a transfer NEVER fails because of the network. The leg is already
 *    written before this class is called, and nothing here can undo it.
 * 2. Recording a transfer NEVER waits on the network. The POST is a separate
 *    step, and it is bounded and best-effort.
 * 3. Recording a transfer NEVER posts twice. `correlationId` is the outbox's
 *    primary key, so a retried save finds the row already there.
 *
 * ## Why the drain is attempted inline anyway
 *
 * Because a transfer made in front of both people should reach the other phone
 * while they are still looking at it. Failing that, the queue is durable and the
 * next sync drains it - a transfer arriving a minute late is a much smaller
 * problem than one that never arrives, and a far smaller one than the owner
 * being told her payment failed.
 */
@Singleton
class CrossProfileTransferPublisher @Inject constructor(
    private val outbox: TransferOutboxRepository,
    private val config: SyncConfig,
) {

    /**
     * Queues a recorded transfer and makes one attempt to publish it.
     *
     * @param correlationId the same id stamped on the owner's leg. It is the join
     *   key and the idempotency key, and deriving a second one here would break
     *   both.
     * @param fromAccountName his account, named because his id is not ours.
     * @param toAccountName their account, named for the same reason.
     * @param occurredOn the calendar day, sent as an ISO date with no time. See
     *   [isoDate].
     * @return what the drain achieved, so a caller could report it. Nothing
     *   throws and nothing here is worth showing the owner either way.
     */
    suspend fun publishRecorded(
        correlationId: UUID,
        fromAccountName: String,
        toAccountName: String,
        amount: Double,
        currency: String,
        occurredOn: Instant,
    ): TransferOutboxRepository.DrainResult {
        val queued = outbox.enqueueEvent(
            correlationId = correlationId,
            recordedBy = config.profileId.orFallback(correlationId),
            amount = amount,
            currency = currency,
            occurredOn = isoDate(occurredOn),
            fromAccountName = fromAccountName,
            toAccountName = toAccountName,
            enqueuedAt = System.currentTimeMillis(),
        )

        if (!config.isConfigured) {
            // Queued anyway, and deliberately. She may sign in to sync later, and
            // a transfer recorded before that must still reach the other phone
            // rather than vanishing. The row is inert until a server exists.
            Timber.i("Transfer $correlationId queued; sync is not configured yet")
            return TransferOutboxRepository.DrainResult(
                published = 0,
                alreadyThere = 0,
                deferred = 0,
            )
        }

        if (!queued) {
            // Already queued, so already in flight or already published. Draining
            // anyway is free and covers the case where the first attempt failed.
            Timber.i("Transfer $correlationId was already queued; draining again")
        }

        val result = outbox.drain()
        if (!result.complete) {
            // Stated plainly rather than as a warning to the owner: the row is
            // safe and will be retried, and she has already recorded the transfer.
            Timber.i(
                "Transfer $correlationId is recorded locally; " +
                    "${result.deferred} transfer(s) still queued for the server"
            )
        }
        return result
    }

    /**
     * The wire format for a transfer's date: `yyyy-MM-dd`, no time.
     *
     * UTC rather than the local zone, and it is a real limitation worth stating.
     * The server stores "the day this happened" with no time, and reconstructing
     * it in the device's own zone would let a transfer recorded at 23:30 in
     * UTC+7 publish as the NEXT day, which is how a transfer silently moves a
     * day on the other phone. UTC is the one reading both phones agree on, and
     * it matches what the owner typed.
     */
    private fun isoDate(instant: Instant): String =
        instant.atZone(ZoneOffset.UTC).toLocalDate().toString()
}

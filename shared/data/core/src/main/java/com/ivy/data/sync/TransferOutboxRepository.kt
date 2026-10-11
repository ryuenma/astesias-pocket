package com.ivy.data.sync

import com.ivy.data.db.dao.read.TransferOutboxDao
import com.ivy.data.db.dao.write.TransferOutboxWrite
import com.ivy.data.db.dao.write.WriteTransferOutboxDao
import com.ivy.data.db.entity.TransferOutboxEntity
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The outbox: writes the transport queue and drains it to the server.
 *
 * The local leg is NEVER blocked on the network - the transfer is written to
 * `transactions` unconditionally and this queue is what makes that safe. Every
 * method returns what happened and never throws, because the drain runs in a
 * coroutine nobody is watching. Full reasoning: docs/decisions/sync-design.md
 */
@Singleton
class TransferOutboxRepository @Inject constructor(
    private val api: TransferSyncApi,
    private val dao: TransferOutboxDao,
    private val writer: WriteTransferOutboxDao,
    private val config: SyncConfig,
) {

    /**
     * Records that a transfer happened and the server does not know yet.
     *
     * Called immediately after the local leg is written, in the same logical
     * step. Not inside a network call, and not conditional on one.
     *
     * @return true when a new row was queued, false when this device had already
     *   queued this transfer. The second is the retry case and is not an error.
     */
    suspend fun enqueueEvent(
        correlationId: UUID,
        recordedBy: String,
        amount: Double,
        currency: String,
        occurredOn: String,
        fromAccountName: String?,
        toAccountName: String?,
        enqueuedAt: Long,
    ): Boolean {
        val row = TransferOutboxEntity(
            correlationId = correlationId,
            kind = TransferOutboxEntity.KIND_EVENT,
            recordedBy = recordedBy,
            amount = amount,
            currency = currency,
            occurredOn = occurredOn,
            fromAccountName = fromAccountName,
            toAccountName = toAccountName,
            state = TransferOutboxEntity.STATE_PENDING,
            enqueuedAt = enqueuedAt,
        )
        val queued = TransferOutboxWrite.wasQueued(writer.enqueueIfAbsent(row))
        if (queued) {
            Timber.i("Queued transfer $correlationId for publishing")
        } else {
            Timber.i("Transfer $correlationId was already queued; not queueing it twice")
        }
        return queued
    }

    /**
     * Records her answer and that the server does not know yet.
     *
     * Same guarantee as [enqueueEvent]: the decision is already durable in
     * `pending_transfers` before this runs, so a failed publish delays the owner
     * learning the outcome and nothing else.
     *
     * There is deliberately NO `decidedBy` parameter. The server's rule is
     * `@request.auth.profileId = @request.body.decidedBy`, so the value has to be
     * the profile of the TOKEN doing the POST - and only this device knows that.
     * A caller-supplied id could be another profile's, which the server would
     * then refuse, and worse, could not be distinguished from an attempt to
     * forge an answer on someone else's behalf.
     *
     * @return true when a new row was queued, false when this transfer already
     *   had a row - which for a confirmation means she already answered, and the
     *   answer is already queued.
     */
    suspend fun enqueueConfirmation(
        correlationId: UUID,
        decision: String,
        enqueuedAt: Long,
    ): Boolean {
        val row = TransferOutboxEntity(
            correlationId = correlationId,
            kind = TransferOutboxEntity.KIND_CONFIRMATION,
            recordedBy = config.profileId.orFallback(correlationId),
            decision = decision,
            state = TransferOutboxEntity.STATE_PENDING,
            enqueuedAt = enqueuedAt,
        )
        val queued = TransferOutboxWrite.wasQueued(writer.enqueueIfAbsent(row))
        if (queued) {
            Timber.i("Queued confirmation $correlationId ($decision)")
        } else {
            Timber.i("Confirmation $correlationId was already queued")
        }
        return queued
    }

    /**
     * Publishes everything queued, oldest first.
     *
     * Bounded per call. A phone that has been offline for a month must not spend
     * a whole battery charge posting hundreds of rows before the owner can do
     * anything else; the remainder goes out on the next call.
     *
     * Every outcome is per-row and no row aborts the others: one malformed
     * transfer that the server always refuses must not strand every transfer
     * recorded after it.
     *
     * @return how it went, so a caller can log a partial drain honestly rather
     *   than reporting success because the loop finished.
     */
    suspend fun drain(limit: Int = DEFAULT_BATCH): DrainResult {
        val pending = dao.findPending(limit)
        if (pending.isEmpty()) {
            return DrainResult(published = 0, alreadyThere = 0, deferred = 0)
        }

        var published = 0
        var alreadyThere = 0
        var deferred = 0

        for (row in pending) {
            when (val result = publish(row)) {
                is TransferSyncApi.PublishResult.Accepted -> {
                    if (TransferOutboxWrite.wasMarkedSent(writer.markSent(row.correlationId, now()))) {
                        published++
                        Timber.i("Published ${row.kind} ${row.correlationId}")
                    } else {
                        // Another drain marked it while this one was in flight.
                        // Already published either way; counting it would double
                        // the reported total.
                        alreadyThere++
                    }
                }

                is TransferSyncApi.PublishResult.AlreadyPublished -> {
                    // The server's unique index did its job: the record exists
                    // and is correct. Clearing the row here is what stops this
                    // device retrying a transfer that is already on the other
                    // phone, forever, because it cannot tell a refusal from a
                    // failure.
                    if (TransferOutboxWrite.wasMarkedSent(
                            writer.markSent(row.correlationId, now())
                        )
                    ) {
                        alreadyThere++
                        Timber.i("${row.kind} ${row.correlationId} was already on the server")
                    }
                }

                is TransferSyncApi.PublishResult.Undelivered -> {
                    writer.recordFailure(row.correlationId, result.reason)
                    deferred++
                    Timber.w(
                        "${row.kind} ${row.correlationId} stays queued: ${result.reason}"
                    )
                }
            }
        }

        Timber.i(
            "Outbox drain: $published published, $alreadyThere already there, " +
                "$deferred deferred, ${pending.size} examined"
        )
        return DrainResult(
            published = published,
            alreadyThere = alreadyThere,
            deferred = deferred,
        )
    }

    /** How much is still waiting. Drives an "N unsent" affordance. */
    suspend fun pendingCount(): Int = dao.countPending()

    /**
     * Whether this device is the one that recorded this transfer.
     *
     * Used on the read side: an arriving event is only news if some other
     * profile recorded it. Without this check our own event comes back in the
     * same feed and lands in the inbox as a second, mirrored copy of a transfer
     * already in the ledger.
     */
    suspend fun isOwnEvent(correlationId: UUID): Boolean =
        dao.findState(correlationId) != null

    /**
     * The retained queue row for this transfer, or null when this device never
     * queued one.
     *
     * [isOwnEvent] answers a yes/no question, which is all the inbound event
     * pull needs. This returns the row so a caller can also ask WHICH kind of row
     * it is, which the answer pull genuinely needs: this device retains both
     * TRANSFER_EVENT rows (money it sent) and CONFIRMATION rows (answers it
     * gave), and an incoming confirmation that matches a CONFIRMATION row is
     * about somebody else's transfer - recording it would tell the owner her own
     * answer was news about her money.
     */
    suspend fun ownEvent(correlationId: UUID): TransferOutboxEntity? =
        dao.findByCorrelationId(correlationId)

    /**
     * One publish attempt for one row.
     *
     * A block body rather than an expression because the two early exits below
     * are the interesting part of this function and an expression body would
     * bury them inside a `when`.
     */
    private suspend fun publish(row: TransferOutboxEntity): TransferSyncApi.PublishResult {
        // A queued event always carries these. A null means the row was written
        // by something other than `enqueueEvent`, and posting 0.00 would be a
        // lie about money, so it is refused rather than defaulted.
        val amount = row.amount
        if (row.kind == TransferOutboxEntity.KIND_EVENT && amount == null) {
            return TransferSyncApi.PublishResult.Undelivered("queued event has no amount")
        }

        // Same reasoning for an answer with no decision: the server field is a
        // select of exactly two values, and an empty string is not one of them.
        val decision = row.decision
        if (row.kind == TransferOutboxEntity.KIND_CONFIRMATION && decision == null) {
            return TransferSyncApi.PublishResult.Undelivered("queued confirmation has no decision")
        }

        return when (row.kind) {
            TransferOutboxEntity.KIND_EVENT -> api.publishEvent(
                correlationId = row.correlationId.toString(),
                recordedBy = row.recordedBy,
                amount = amount ?: 0.0,
                currency = row.currency.orEmpty(),
                occurredOn = row.occurredOn.orEmpty(),
                fromAccountName = row.fromAccountName,
                toAccountName = row.toAccountName,
            )

            TransferOutboxEntity.KIND_CONFIRMATION -> api.publishConfirmation(
                correlationId = row.correlationId.toString(),
                decidedBy = row.recordedBy,
                decision = decision.orEmpty(),
            )

            // An unknown kind is not published and not guessed at. Left PENDING
            // with the reason recorded, which is diagnosable; interpreted, it
            // would be untraceable.
            else -> TransferSyncApi.PublishResult.Undelivered("unknown kind '${row.kind}'")
        }
    }

    private fun now(): Long = System.currentTimeMillis()

    /**
     * What one drain achieved.
     *
     * [alreadyThere] is counted separately from [published] rather than folded
     * into it, and the distinction is the point: a server that keeps refusing a
     * replay as a duplicate looks identical to a healthy drain from the point of
     * view of "did the money arrive", but only one of them means this device is
     * still behind. Reporting both as "published" would hide a device that has
     * been quietly re-sending the same row every sync.
     *
     * [deferred] is the honest word for the rest: the row is intact and queued,
     * nothing was lost, and the owner does not need to know or do anything.
     */
    data class DrainResult(
        val published: Int,
        val alreadyThere: Int,
        val deferred: Int,
    ) {
        /** How many rows this device still owes the server after the drain. */
        val stillQueued: Int
            get() = deferred

        /** Whether everything examined made it to the server this time. */
        val complete: Boolean
            get() = deferred == 0
    }

    private companion object {
        /**
         * Rows per drain.
         *
         * Bounded so one drain cannot monopolise a background window on a phone
         * that has been offline long enough to queue a lot.
         */
        const val DEFAULT_BATCH = 25
    }
}

/**
 * This device's profile id, or a per-transfer stand-in when it is unknown.
 *
 * The server's `decidedBy` and `recordedBy` are required fields, and an empty
 * value would be refused as invalid - which the drain would then correctly treat
 * as permanent and retry forever. The stand-in is derived from the correlation
 * id, so it is stable across retries (a retry must not look like a different
 * author) and unique per transfer, so it can never be mistaken for another
 * profile's event.
 */
fun String?.orFallback(correlationId: UUID): String {
    if (!isNullOrBlank()) return this

    // The WHOLE id, not a prefix of it. A prefix would be shorter but a
    // collision across two transfers would then attribute one to the other,
    // which is a worse failure than a longer string in a log line.
    return "unknown-$correlationId"
}

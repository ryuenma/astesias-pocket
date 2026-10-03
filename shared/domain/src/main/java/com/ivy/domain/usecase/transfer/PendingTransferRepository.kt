package com.ivy.domain.usecase.transfer

import com.ivy.data.db.dao.read.PendingTransferDao
import com.ivy.data.db.dao.read.PendingTransferWrite
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.PendingTransferEntity
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The counterpart's side of a mirrored transfer: what arrives, what waits, and
 * what she decides.
 *
 * This is where the idempotency rules meet the database. The decisions live in
 * [TransferInbox] as pure functions; this class applies them, and the safety
 * net underneath is the inbox table's primary key on `correlationId`. Both are
 * needed and neither is sufficient alone:
 *
 *  - [TransferInbox] stops a replayed delivery of a *known* transfer from
 *    rewriting her decision.
 *  - The primary key stops two *simultaneous* deliveries of an unknown transfer
 *    from creating two rows, which no read-then-write check can catch because
 *    both read "not seen yet" before either commits.
 *
 * Everything here returns a value describing what happened rather than throwing.
 * A failure to sync must never take down a wallet, and the counterpart's inbox
 * is the least valuable thing on the device to lose in an exception.
 */
@Singleton
class PendingTransferRepository @Inject constructor(
    private val dao: PendingTransferDao,
    private val transactionDao: WriteTransactionDao,
) {

    /**
     * Handles one transfer event arriving from sync.
     *
     * @return what was done, so a caller can log a replay as a replay instead of
     *   mistaking it for new data.
     */
    suspend fun onEventArrived(item: PendingTransferEntity): TransferInbox.Outcome {
        // Read first, then let the pure function decide. The read is an
        // optimisation and a courtesy to the log; the PRIMARY KEY is what
        // actually enforces uniqueness, so a race between two callers still
        // cannot produce two rows.
        val existing = dao.findState(item.correlationId)?.toStateOrNull()
        val outcome = TransferInbox.onEventArrived(existing)

        if (!outcome.writesNewRow()) {
            Timber.i(
                "Transfer ${item.correlationId} already known as $existing; " +
                    "delivery is a replay, nothing written"
            )
            return outcome
        }

        // IGNORE, not REPLACE: if a concurrent delivery won the race between the
        // read above and here, this insert is refused rather than overwriting a
        // decision that may already have been made.
        val insertId = dao.insertIfAbsent(item)
        if (PendingTransferWrite.isNewRow(insertId)) {
            Timber.i("Transfer ${item.correlationId} arrived and is pending confirmation")
        } else {
            Timber.i("Transfer ${item.correlationId} lost an insert race; treated as replay")
        }
        // The outcome is the decision, not the insert result: both "we created
        // it" and "someone beat us to it" are Create, and both mean the correct
        // end state - exactly one PENDING row.
        return outcome
    }

    /** Items still waiting on her. */
    suspend fun pending(): List<PendingTransferEntity> = dao.findPending()

    /**
     * Applies her decision.
     *
     * ACCEPT writes the real TRANSFER row. DECLINE writes only the tombstone: the
     * owner's leg is untouched, because the money already moved and her declining
     * it cannot un-move it. Retracting the owner's row from here would falsify
     * the owner's ledger, which is the worst outcome this feature could produce.
     *
     * @param senderAccountId the counterparty's account this money left, named as
     *   an id for the leg even though it does not resolve in her database. It is
     *   carried for joinability, never resolved locally.
     */
    suspend fun decide(
        correlationId: UUID,
        action: TransferInbox.Action,
        confirmedLegId: UUID,
        senderAccountId: UUID,
    ): TransferInbox.Decision {
        val existing = dao.findState(correlationId)?.toStateOrNull()
        val decision = TransferInbox.onUserDecision(existing, action)

        if (decision == TransferInbox.Decision.NoChange) {
            Timber.i("Decision on $correlationId is a no-op (state=$existing)")
            return decision
        }

        // Read the payload before mutating, so the leg is built from data that
        // was already PENDING rather than re-reading a row we just changed.
        val item = dao.findByCorrelationId(correlationId)
        if (item == null) {
            Timber.w("Decision on $correlationId found no inbox row; nothing to apply")
            return TransferInbox.Decision.NoChange
        }

        // Guarded on PENDING, so a double-tap writes nothing the second time.
        val rows = dao.markDecided(
            correlationId = correlationId,
            state = when (decision) {
                TransferInbox.Decision.Accept -> PendingTransferState.ACCEPTED.name
                else -> PendingTransferState.DECLINED.name
            }
        )
        if (!PendingTransferWrite.decisionApplied(rows)) {
            // Lost the race against a concurrent tap. Her decision is already
            // recorded, so writing the ledger row now could double it.
            Timber.i("Decision on $correlationId already applied elsewhere; not repeating")
            return TransferInbox.Decision.NoChange
        }

        if (decision.touchesLedger()) {
            transactionDao.save(
                CrossProfileTransfer.receiverLeg(
                    legId = confirmedLegId,
                    correlationId = correlationId,
                    intoAccountId = item.intoAccountId,
                    fromAccountId = senderAccountId,
                    senderProfileId = item.recordedByProfileId,
                    amount = item.amount,
                    dateTime = item.occurredOn,
                    title = item.title,
                    description = item.description,
                )
            )
            Timber.i("Transfer $correlationId confirmed into ledger as $confirmedLegId")
        } else {
            Timber.i("Transfer $correlationId declined; her ledger untouched")
        }

        return decision
    }
}

/**
 * Parses a stored state string, tolerating anything unrecognised.
 *
 * An unknown value is treated as [PendingTransferState.PENDING] rather than
 * throwing or defaulting to a terminal state: PENDING is the conservative
 * choice, because it is the only one that cannot silently discard a decision.
 * A value we cannot read still shows up in her inbox and can still be acted on.
 */
private fun String.toStateOrNull(): PendingTransferState? =
    runCatching { PendingTransferState.valueOf(this) }.getOrNull() ?: PendingTransferState.PENDING
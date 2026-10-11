package com.ivy.domain.usecase.transfer

import com.ivy.data.db.dao.read.TransferConfirmationDao
import com.ivy.data.db.dao.write.TransferConfirmationWrite
import com.ivy.data.db.dao.write.WriteTransferConfirmationDao
import com.ivy.data.db.entity.TransferConfirmationEntity
import com.ivy.data.db.entity.TransferConfirmationWithEvent
import com.ivy.data.db.entity.TransferOutboxEntity
import com.ivy.data.sync.SyncConfig
import com.ivy.data.sync.TransferOutboxRepository
import com.ivy.data.sync.TransferSyncApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers arriving about transfers WE sent: the counterpart accepted or declined.
 *
 * Only recorded when the retained outbox row is kind EVENT, so the owner's own
 * answer coming back in the shared feed is never shown as news about money she
 * sent. Persisted, never a `transactions` write, never throws. Full reasoning:
 * docs/decisions/sync-design.md
 */
@Singleton
class TransferConfirmationRepository @Inject constructor(
    private val api: TransferSyncApi,
    private val dao: TransferConfirmationDao,
    private val writer: WriteTransferConfirmationDao,
    private val outbox: TransferOutboxRepository,
    private val config: SyncConfig,
) {

    /**
     * Reads the counterpart's answers and records the ones about our transfers.
     *
     * Idempotent. A second sync over the same answers writes nothing, because
     * the primary key refuses the duplicate - and refuses it without clearing
     * [TransferConfirmationEntity.acknowledged], so an answer the owner has
     * already read cannot re-light its own badge on every pull thereafter.
     *
     * @return what arrived, so a caller can log a pull honestly instead of
     *   reporting success because the loop finished.
     */
    // ReturnCount: three returns, and each is a different failure the caller
    // has to be able to tell apart. Folding them into one would mean reporting
    // "nothing arrived" for a device with no server and for a server that could
    // not be reached, which are different problems with different fixes.
    @Suppress("ReturnCount")
    suspend fun pullAnswers(): AnswerPullResult {
        if (!config.isConfigured) {
            Timber.i("Sync not configured; recorded answers are left as-is")
            return AnswerPullResult.NotConfigured
        }

        // `fetchConfirmations` is specified never to throw, and this does not
        // depend on that promise: a socket that dies mid-read is an ordinary
        // event on a phone, not a programming error, and it must read as
        // "unreachable" rather than taking down whatever hosted this coroutine.
        val envelope = runCatching { api.fetchConfirmations() }.getOrElse { error ->
            Timber.e(error, "Could not read transfer confirmations")
            null
        } ?: return AnswerPullResult.Unreachable

        var recorded = 0
        var alreadyKnown = 0
        var notOurs = 0
        var unusable = 0

        for (remote in envelope.items) {
            // Per-answer isolation, around the WHOLE body rather than just the
            // validation: the insert is the part most likely to fail (a locked
            // database, a disk full) and an exception there would abandon the
            // answers queued behind it. One row this device cannot process must
            // not cost her the other four that were perfectly fine.
            val outcome = runCatching { record(remote) }.getOrElse { error ->
                Timber.e(error, "Could not process an arriving confirmation")
                Outcome.UNUSABLE
            }

            when (outcome) {
                Outcome.RECORDED -> recorded++
                Outcome.ALREADY_KNOWN -> alreadyKnown++
                Outcome.NOT_OURS -> notOurs++
                Outcome.UNUSABLE -> unusable++
            }
        }

        Timber.i(
            "Pulled answers: $recorded new, $alreadyKnown already known, " +
                "$notOurs not about our transfers, $unusable could not be read"
        )
        return AnswerPullResult.Pulled(
            recorded = recorded,
            alreadyKnown = alreadyKnown,
            notOurs = notOurs,
            unusable = unusable,
        )
    }

    /**
     * One remote answer, resolved against this device and recorded.
     *
     * Every rejection is a deliberate refusal to guess, and each is stated as a
     * value rather than as a chain of early returns so the rules read in order.
     */
    // ReturnCount: the refusals ARE the function. Each early return is a
    // deliberate refusal to record something this device cannot interpret, and
    // every one of them is a rule. Collapsing them into a single expression
    // would make the order they are checked in - the thing that stops a later
    // rule from quietly skipping an earlier one - unreadable.
    @Suppress("ReturnCount")
    private suspend fun record(
        remote: TransferSyncApi.RemoteConfirmation,
    ): Outcome {
        val correlationId = runCatching { UUID.fromString(remote.correlationId) }.getOrNull()
            ?: return refuse("a confirmation whose correlationId will not parse")

        // The server field is a select of exactly two values. Storing an empty or
        // unrecognised one would render as a claim about money this device cannot
        // interpret, and there is no safe default: ACCEPTED and DECLINED mean
        // opposite things, so guessing wrong is a lie in whichever direction it
        // lands.
        if (!remote.decision.isKnownDecision()) {
            return refuse("a confirmation with an unrecognised decision")
        }

        // Is this answer about a transfer WE sent? Two checks, because either
        // alone has a hole - see the class doc.
        val own = outbox.ownEvent(correlationId) ?: return Outcome.NOT_OURS
        if (own.kind != TransferOutboxEntity.KIND_EVENT) {
            // The row is a CONFIRMATION this device published, so the answer
            // matching it concerns a transfer that arrived here. Not news about
            // money she sent.
            return Outcome.NOT_OURS
        }

        val row = TransferConfirmationEntity(
            correlationId = correlationId,
            decision = remote.decision,
            decidedBy = remote.decidedBy,
            // The only instant actually known here. See the entity's field doc:
            // the wire carries no decision timestamp and inventing one would put
            // a false precision on the row.
            recordedAt = now(),
            acknowledged = false,
        )

        return if (TransferConfirmationWrite.wasRecorded(writer.recordIfAbsent(row))) {
            Timber.i("Partner ${remote.decidedBy} answered $correlationId: ${remote.decision}")
            Outcome.RECORDED
        } else {
            // The primary key refused it. Reported as a replay rather than as a
            // failure, and deliberately NOT retried with REPLACE: that would
            // write acknowledged = 0 over a row the owner has already read and
            // make her badge permanent.
            Outcome.ALREADY_KNOWN
        }
    }

    private fun refuse(why: String): Outcome {
        Timber.w("Dropping $why")
        return Outcome.UNUSABLE
    }

    /**
     * Recorded answers joined to the transfers they are about, newest first.
     *
     * The read the owner's list renders from, and deliberately a read of what is
     * DURABLY known rather than of whatever the last pull returned - the whole
     * point of persisting is that the answer is still here after the sync that
     * delivered it is long gone.
     */
    suspend fun answered(): List<TransferConfirmationWithEvent> = dao.findAllWithEvent()

    /** How many answers the owner has not read. Drives the Settings badge. */
    suspend fun unacknowledgedCount(): Int = dao.countUnacknowledged()

    /**
     * Marks every recorded answer as read.
     *
     * Called when she opens the list: having opened it, she has seen it. Not a
     * deletion - the answers stay readable, because "what happened to the money
     * I sent" is a question she can ask again next month.
     */
    suspend fun acknowledgeAll(): Int = writer.acknowledgeAll()

    private fun now(): Long = System.currentTimeMillis()

    /** How one answer's processing ended. */
    private enum class Outcome {
        /** A new row now exists. */
        RECORDED,

        /** Already recorded. A redelivery, not an error. */
        ALREADY_KNOWN,

        /**
         * About a transfer this device did not send.
         *
         * Expected and routine: every profile reads every confirmation, so the
         * partner's own answers to their own transfers are in this feed too, and
         * so is this device's own answer coming back.
         */
        NOT_OURS,

        /** Could not be read. Left on the server for a later attempt. */
        UNUSABLE,
    }

    /**
     * What one pull of the answers achieved.
     *
     * [recorded] and [alreadyKnown] are counted apart for the same reason the
     * outbox drain separates `published` from `alreadyThere`: a device that
     * re-reads the same answers on every sync and records nothing new is
     * behaving correctly, and folding the two together would make that
     * indistinguishable from progress.
     *
     * [notOurs] is reported rather than hidden. It is the count of answers about
     * other people's transfers that arrived in this feed, which is information
     * about the feed rather than a failure.
     */
    sealed interface AnswerPullResult {
        /** Nothing was attempted: this device has no server. */
        data object NotConfigured : AnswerPullResult

        /** The server could not be read. Recorded answers are untouched. */
        data object Unreachable : AnswerPullResult

        data class Pulled(
            val recorded: Int,
            val alreadyKnown: Int,
            val notOurs: Int,
            val unusable: Int,
        ) : AnswerPullResult
    }
}

/**
 * Whether a wire decision is one of the two the server can store.
 *
 * Deliberately a whitelist rather than "not blank". The wire is a String with a
 * default of `""`, so anything can arrive, and the two values mean opposite
 * things - there is no third reading of an unrecognised one that is safer than
 * storing it.
 */
private fun String.isKnownDecision(): Boolean =
    this == TransferOutboxEntity.DECISION_ACCEPTED ||
        this == TransferOutboxEntity.DECISION_DECLINED

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
 * The owner's side of a mirrored transfer: what the counterpart ANSWERED.
 *
 * ## Why this class exists
 *
 * Because the answer was being thrown away. `TransferSyncApi.pullConfirmations`
 * read every confirmation on the server and handed the list to a caller that
 * discarded it, so the person who sent the money never learned it landed. The
 * leg left her account the instant she tapped save and that was always durable -
 * what was missing was the one fact that made the payment complete.
 *
 * [TransferEventSync] owns the other direction (events arriving for HER inbox);
 * this owns this one (answers arriving about HER transfers). They are separate
 * because they are separate tables with separate rules, and folding them
 * together is how "our own answer comes back and is recorded as the partner's"
 * becomes possible.
 *
 * ## The load-bearing rule: only record answers about transfers WE recorded
 *
 * Two independent checks, because either alone has a hole.
 *
 * The server lets both profiles read every confirmation, so this device
 * necessarily sees the answer the OWNER published herself coming back in the same
 * feed. Recording it would tell her the partner accepted a transfer she had
 * already accepted - a fact about money that is false, sourced from a real row.
 * [TransferOutboxRepository.isOwnEvent] plus the `kind` check close that: an
 * answer is only recorded when the retained outbox row for that correlation id
 * is an EVENT, meaning this device is the one that sent the transfer.
 *
 * That second half is the part worth being careful about. This device also
 * retains outbox rows of kind CONFIRMATION - the answers SHE gave about
 * transfers that arrived. Those are not her transfers, so an answer matching one
 * of those keys belongs to somebody else's transfer and must not be shown as
 * news about money she sent.
 *
 * ## Why the answer is persisted rather than shown as a snackbar
 *
 * Because a snackbar is only evidence if she happens to be looking at the
 * screen at the moment the sync completes, which for a background sync is most
 * of the time. "Your transfer was accepted" that she never saw is not
 * information. A row is: it survives the app being closed, survives a drain,
 * survives the queue being emptied, and is still there next month when she
 * opens Settings and asks what happened to the money she sent on the 5th.
 *
 * That is also why [TransferConfirmationEntity.acknowledged] exists. Without
 * it, persistence would be a list she has to remember to re-read; with it, the
 * unacknowledged count is a truthful answer to "is there anything I have not
 * looked at yet".
 *
 * ## It never writes to the ledger
 *
 * Not one row here touches `transactions`. The money already left her account
 * when she saved it, and the counterpart accepting or declining it does not
 * retroactively change that - see [TransferInbox.Decision.touchesLedger] for the
 * argument in full. What this class writes is knowledge about a fact, never the
 * fact itself.
 *
 * ## Never throws
 *
 * Every method returns a description of what happened. The counterpart's answer
 * is the least valuable thing on the device to lose in a stack trace, and this
 * runs inside background syncs nobody is watching.
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

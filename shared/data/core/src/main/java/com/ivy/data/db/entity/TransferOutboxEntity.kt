package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Something this device still owes the sync server.
 *
 * ## What this table is FOR, which is the whole reason it exists
 *
 * The owner's leg of a cross-profile transfer is written to `transactions` the
 * instant she taps save, and unconditionally, because the money has already
 * moved by then. A row that waited on a network round-trip to appear would be a
 * lie about money that is already gone, and a wallet that refuses to record a
 * payment because the other phone is on a train is worse than no wallet at all.
 *
 * That leaves a gap with a name: the server has never heard about the transfer.
 * This table is that gap, made durable. A row is written in the same local
 * transaction as the leg itself, so the two cannot disagree, and a later drain
 * publishes it. If the process dies between the two, the row is still there.
 *
 * ## Why a separate table and not a flag on `transactions`
 *
 * The same reasoning that put `pending_transfers` and `pending_bills` in tables
 * of their own. Every balance, report, CSV export and XLSX export in this
 * codebase reads `transactions` - `AccountBalanceUseCase` sums what it is
 * handed with no filter at all - so anything parked there is eventually counted
 * by a query somebody forgot to exclude. Keeping the transport queue out of the
 * ledger makes that impossible rather than a rule to remember.
 *
 * The symmetry with `pending_transfers` is deliberate and worth stating: that
 * table holds what the COUNTERPART is waiting to decide, this one holds what
 * this device still has to TELL the server. Both are outside the ledger, both
 * are keyed on `correlationId`, and neither can move a balance.
 *
 * ## `correlationId` is the PRIMARY KEY, and that is the load-bearing part
 *
 * It is the same key the server's unique index enforces, so the two ends agree
 * on identity rather than each guessing. Enqueueing the same transfer twice -
 * a retried save, a WorkManager retry, a re-sync - collides here and is refused,
 * which means at most ONE POST is ever issued per transfer from this device.
 *
 * This is the property that has already broken once on this project: a
 * PocketBase collection declared `unique: true` on a field with no real index
 * behind it, so a replayed POST returned 200 and wrote a second row - the same
 * real-world transfer recorded twice. A real PRIMARY KEY is the difference
 * between impossible and unlikely.
 *
 * `correlationId` alone is the key rather than `(kind, correlationId)` because
 * one device never owes both an event and a confirmation for the same
 * transfer: the owner publishes the event, the counterpart publishes the
 * confirmation, and those are different phones with different databases.
 *
 * ## A SENT row is RETAINED, never deleted
 *
 * Same reason a DECLINED transfer keeps its row. Deleting on success would let
 * the next enqueue - the retried save, the re-sync - insert a fresh row and POST
 * the same transfer again, and the server would then reject it as a duplicate
 * while this device kept retrying a row it had already satisfied. A tombstone
 * that says "this was published" is what makes the replay a no-op.
 *
 * Deliberately NOT `@Serializable`: like [PendingTransferEntity] and
 * [PendingBillEntity] this is local working state, never part of the owner's
 * Ivy backup export.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Entity(tableName = "transfer_outbox")
data class TransferOutboxEntity(
    /**
     * Correlation id, and the primary key. The same key the server indexes,
     * which is what makes one transfer one POST. See the class doc.
     */
    @PrimaryKey
    val correlationId: UUID,

    /** TRANSFER_EVENT or CONFIRMATION. Decides which collection is posted to. */
    val kind: String,

    /** Profile that recorded the event, or that answered it. */
    val recordedBy: String,

    /** Null for a confirmation, which carries no money. */
    val amount: Double? = null,

    /** Null for a confirmation. */
    val currency: String? = null,

    /**
     * ISO calendar date, `yyyy-MM-dd`, and no time.
     *
     * A string rather than an Instant so a drain republishes byte-for-byte what
     * was enqueued: reformatting on the way out would mean a retry could post a
     * different date than the first attempt if a zone rule ever changed
     * underneath it.
     */
    val occurredOn: String? = null,

    /** Name of the account the money left. A name, never an id - see below. */
    val fromAccountName: String? = null,

    /**
     * Name of the account the money arrived into, as the SENDER knows it.
     *
     * Kept for backward compatibility with rows recorded before
     * [toAccountId] existed, and as the fallback when a legacy event carries
     * no id. The name the receiver resolves against is her own, so a sender's
     * spelling that drifted from the receiver's actual account name is exactly
     * the failure mode this field alone cannot escape - see [toAccountId].
     */
    val toAccountName: String? = null,

    /**
     * The destination account's id as published in the shared directory.
     *
     * A UUID, not a directory foreign key: it is the `accountId` of the
     * directory row the sender picked for the destination, and the receiver
     * resolves it through her own copy of that directory - see
     * `DirectoryAccountEntity`, whose `accountId` is the account owner's own
     * local Room id. Carrying the id on the wire is what lets a transfer be
     * placed even when the two devices spell the account differently, which
     * the name alone could never guarantee.
     *
     * Null only for rows recorded before this field existed; those are
     * published without it on the wire and matched by name on the way in.
     * Never set for a [KIND_CONFIRMATION] row, which carries no account.
     */
    val toAccountId: UUID? = null,

    /** ACCEPTED or DECLINED, for a confirmation only. An enum on the wire. */
    val decision: String? = null,

    /** PENDING until the server accepts it; SENT forever after. */
    val state: String,

    /**
     * Permanent refusals so far - the give-up counter, and nothing else.
     *
     * A transient failure (offline, timeout, expired token, sync not configured)
     * does NOT increment this, because that class heals itself and counting it
     * would make a phone that queued transfers before signing in terminal on
     * money that was always going to publish. At
     * [TransferOutboxEntity.MAX_REJECTIONS] the row leaves the PENDING queue as
     * [TransferOutboxEntity.STATE_FAILED].
     */
    val attempts: Int = 0,

    /** Why the last attempt failed, so a stuck row can be explained. */
    val lastError: String? = null,

    /** Epoch millis, so the drain can send oldest-first. */
    val enqueuedAt: Long,

    /** Epoch millis of the accepted POST. Null until then. */
    val sentAt: Long? = null,
) {
    companion object {
        /** A transfer event: the owner's leg, heading for `transfer_events`. */
        const val KIND_EVENT = "TRANSFER_EVENT"

        /** Her answer, heading for `transfer_confirmations`. */
        const val KIND_CONFIRMATION = "CONFIRMATION"

        /** Queued. The only state a drain reads. */
        const val STATE_PENDING = "PENDING"

        /** The server has it. Terminal and retained - see the class doc. */
        const val STATE_SENT = "SENT"

        /**
         * The server refused this row [MAX_REJECTIONS] times.
         *
         * Terminal and retained, exactly like [STATE_SENT]. A drain reads only
         * [STATE_PENDING], so this is what takes a permanently refused row out
         * of the batch. Without it such a row owns a slot in every drain
         * forever, and since `findPending` is oldest-first and capped at 25,
         * twenty-five of them stop every newer transfer being attempted at all.
         *
         * The row is NOT deleted. `isOwnEvent()` reads it, and a deletion would
         * let our own published event return in the feed as a second, mirrored
         * copy of a transfer already in the ledger.
         */
        const val STATE_FAILED = "FAILED"

        /**
         * Permanent refusals before a row becomes [STATE_FAILED].
         *
         * Only refusals the server will repeat count toward this - see
         * [TransferOutboxEntity.attempts]. Five is enough that a rule deployed
         * mid-sync is not mistaken for a permanent one, and small enough that a
         * genuinely unacceptable row leaves the queue the same day.
         */
        const val MAX_REJECTIONS = 5

        /** The wire values of [decision]. An enum so no typo invents a third. */
        const val DECISION_ACCEPTED = "ACCEPTED"

        /** See [DECISION_ACCEPTED]. */
        const val DECISION_DECLINED = "DECLINED"
    }
}
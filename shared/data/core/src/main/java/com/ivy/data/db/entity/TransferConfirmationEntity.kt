package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * What the counterpart decided about a transfer THIS device recorded.
 *
 * ## Why this table exists at all
 *
 * Because the answer was being thrown away. `TransferSyncApi.pullConfirmations`
 * read every confirmation the server held and handed the list to a caller that
 * discarded it, so the person who sent the money never learned that it landed.
 * The money left her account the instant she tapped save - that part was always
 * durable - and then the one fact that made the payment complete was dropped on
 * the floor. This table is where that fact goes instead of nowhere.
 *
 * ## Why a table and not a column on `transfer_outbox`
 *
 * The outbox is a transport queue: what this device still owes the server, with
 * rows cleared to SENT once published. The answer is the opposite direction and
 * the opposite lifetime - it arrives after the queue is empty, and it has to
 * survive the queue being drained, a re-sync, and the account being reopened next
 * month. Parking it on a queue row that is retained as a tombstone and then
 * repurposed to mean "she answered" as well would make one row carry two
 * unrelated claims, and `deleteAll()` on sign-out would take the owner's record
 * of what happened to her money with it.
 *
 * The same reasoning that put `pending_transfers` and `pending_bills` in tables
 * of their own applies here. And like both of those, nothing in this table is
 * ever in `transactions`, so no answer can move a balance - the owner's leg
 * stands exactly as she recorded it whatever the counterpart decides.
 *
 * ## `correlationId` is the PRIMARY KEY, and that is load-bearing
 *
 * It is the cross-profile join key, and it is the idempotency key. Every profile
 * may read every confirmation, so the owner's device necessarily sees the answer
 * she published herself coming back in the same feed; on top of that, a sync
 * that runs twice delivers the same answer twice. `correlationId` as the key
 * means the second delivery collides here and is refused rather than inserting a
 * second row or - far worse - resetting a row the owner has already read back to
 * unread.
 *
 * ## It records the ANSWER, not the money
 *
 * Deliberately no amount, currency, account name or date on this row. Those
 * belong to the transfer, they are already held on the retained `transfer_outbox`
 * event row under this same key, and copying them here would create a second
 * copy that can drift from the first. [TransferConfirmationWithEvent] joins the
 * two for display instead.
 *
 * Deliberately NOT `@Serializable`: like [PendingTransferEntity] this is
 * sync-derived local state, never part of the owner's Ivy backup export.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Entity(tableName = "transfer_confirmations")
data class TransferConfirmationEntity(
    /**
     * Correlation id of the transfer this answers, and the primary key.
     *
     * See the class doc for why the key is the idempotency guarantee rather than
     * an implementation detail.
     */
    @PrimaryKey
    val correlationId: UUID,

    /**
     * ACCEPTED or DECLINED, the server's own two values.
     *
     * Shared with [TransferOutboxEntity.DECISION_ACCEPTED] and
     * [TransferOutboxEntity.DECISION_DECLINED] rather than re-declared: the wire
     * enum has exactly two values and a third spelling one of them is how a
     * recorded answer becomes permanently unreadable.
     */
    val decision: String,

    /**
     * Profile that decided. Never this device's own profile - see
     * `TransferConfirmationRepository`, which refuses that row rather than
     * recording the owner's own answer as though the partner had given it.
     */
    val decidedBy: String,

    /**
     * Epoch millis at which THIS device learned the answer.
     *
     * Not the moment she decided: the wire carries no timestamp for a
     * confirmation, and inventing one - from the transfer's date, or from now
     * while pretending it is the decision time - would put a false precision on
     * the row. This is the only instant that is actually known here, and it is
     * what the owner needs anyway: "you learned this at 14:02" is the useful
     * claim, not "she pressed accept at 14:02".
     */
    val recordedAt: Long,

    /**
     * Whether the owner has actually seen this answer.
     *
     * The whole point of persisting rather than showing a snackbar. A snackbar
     * lasts a few seconds and is gone if she is not looking at the screen at the
     * moment the sync happens, which for a background sync is most of the time.
     * A row with no acknowledgement is a fact that will still be there when she
     * next opens the app, which is the only honest way to tell someone that
     * money arrived.
     *
     * Set by [acknowledged] the first time the answer is read back, and never
     * cleared: an answer that has been read does not become unread because the
     * queue drained again, which is exactly why the write is guarded rather than
     * re-derived on every pull.
     */
    val acknowledged: Boolean = false,
)

/**
 * One recorded answer, joined to the transfer it is about.
 *
 * The join exists because the two tables have genuinely disjoint facts and no
 * table needs the other's: the money is on the retained outbox row, the decision
 * is here. Copying the money onto the confirmation would be the alternative and
 * it is worse - two rows claiming the same amount, one of which can be stale.
 *
 * The money fields are nullable even though every row here is joined against a
 * recorded event. Nullable is what makes this safe against a future caller that
 * queries the wrong side, and it forces the display layer to handle "we know the
 * answer but cannot describe the transfer" instead of showing a fabricated 0.00.
 */
// DataClassDefaultValues: the money fields default to null on purpose, and
// DataClassTypedIDs: `correlationId` is a bare UUID because it is the Room
// join key AND the value the server's unique index is on. A value class here
// would change the column type on both sides of a join that has to keep
// matching, which is a much larger risk than the one the rule is guarding
// against. Both suppressions match the sibling entities in this package.
@Suppress("DataClassDefaultValues", "DataClassTypedIDs")
data class TransferConfirmationWithEvent(
    val correlationId: UUID,
    val decision: String,
    val decidedBy: String,
    val recordedAt: Long,
    val acknowledged: Boolean,
    val amount: Double? = null,
    val currency: String? = null,
    val occurredOn: String? = null,
    val fromAccountName: String? = null,
    val toAccountName: String? = null,
)

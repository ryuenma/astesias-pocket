package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.model.TransactionType

/**
 * A monthly bill that has been reminded about and not yet settled by the owner.
 *
 * This is NOT a transaction and it is NOT a row in `transactions`. It is a claim
 * that a bill is coming due, held outside the ledger until a human pays it. The
 * table is separate from `transactions` on purpose: every balance, report, CSV
 * export and XLSX export in this codebase reads `transactions`, so a pending
 * item that lived there would eventually be counted by one of them that nobody
 * remembered to filter. Keeping it here makes that impossible rather than a rule
 * to remember. See `Migration134to135_PendingBills` for the full argument,
 * including the specific totals that would otherwise have counted it.
 *
 * It is still visible in-app and still editable and deletable, because "visible"
 * and "counted" are independent properties and only one of them needed the ledger.
 *
 * [occurrenceKey] is the primary key and is the load-bearing decision of the
 * whole feature rather than an incidental choice: it is the idempotency key. It
 * is derived from the rule id and the clamped occurrence date, so the identity of
 * "the reminder for rule R in month M" is computable without reading anything,
 * and a second delivery of the same reminder collides on the primary key instead
 * of creating a second pending bill. Application-level checks cannot close the
 * window between two in-flight deliveries, because both would read "not seen yet"
 * before either commits.
 *
 * A settled bill is RETAINED as [state] = [STATE_PAID] rather than deleted, for
 * the same reason `pending_transfers` retains DECLINED: deleting it would let the
 * next replay insert a fresh PENDING bill, and the owner would be asked to pay a
 * bill he had already paid, once per replay, with nothing left recording that he
 * had said yes.
 *
 * Deliberately NOT `@Serializable`: like `PendingTransferEntity` this is a local
 * working set, never part of the owner's Ivy backup export. A pending bill is
 * re-derivable from the rule and the month, and round-tripping it through a
 * backup restore would risk resurrecting one the owner had already dismissed.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Entity(tableName = "pending_bills")
data class PendingBillEntity(
    /**
     * Derived idempotency key - the primary key. See the class doc for why this
     * is not a random UUID.
     */
    @PrimaryKey
    val occurrenceKey: String,

    /** The [PlannedPaymentRuleEntity] this bill was fabricated from. */
    val ruleId: String,

    /**
     * Epoch millis of the day the bill is due, at local midnight.
     *
     * Local midnight rather than UTC because the owner thinks in calendar days:
     * a bill due on the 25th is due on the 25th where he is, and converting to
     * UTC would shift it across a day boundary for anyone east or west of
     * Greenwich. The conversion happens once, here, at write time.
     */
    val dueOn: Long,

    /**
     * The amount as of the moment the reminder fired.
     *
     * A copy, not a live reference to the rule, and that is the whole point: the
     * owner can adjust this on the fly without touching the rule, so the rule's
     * amount still describes next month's bill.
     */
    val amount: Double,

    val title: String? = null,
    val description: String? = null,

    /** The account the bill will be paid from, copied from the rule. */
    val accountId: String,

    /** INCOME or EXPENSE, copied from the rule. */
    val type: TransactionType,

    /** One of [STATE_PENDING], [STATE_PAID], [STATE_DISMISSED]. */
    val state: String,
) {
    companion object {
        /**
         * The owner has been reminded and has not acted.
         *
         * The only state that appears in the inbox.
         */
        const val STATE_PENDING = "PENDING"

        /**
         * The owner recorded this bill as a real transaction.
         *
         * Terminal and retained. Never overwritten by a later reminder.
         */
        const val STATE_PAID = "PAID"

        /**
         * The owner deleted this bill without paying it.
         *
         * Terminal and retained, for the same reason [STATE_PAID] is: a deletion
         * that removed the row would let the next reminder for this occurrence
         * re-create it, so a bill he deliberately dismissed would come back on
         * every replay.
         */
        const val STATE_DISMISSED = "DISMISSED"

        /** The only value [occurrenceKey] may take for an undecided bill. */
        const val STATE_PENDING_ONLY = STATE_PENDING
    }
}
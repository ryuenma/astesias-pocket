package com.ivy.legacy.notification

import com.ivy.data.db.dao.read.PendingBillDao
import com.ivy.data.db.dao.write.WritePendingBillDao
import com.ivy.data.db.entity.PendingBillEntity
import org.jetbrains.annotations.VisibleForTesting

/**
 * An in-memory stand-in for `pending_bills` that behaves like SQLite where it
 * matters.
 *
 * Duplicated rather than shared: the canonical copy lives in
 * `shared/domain`'s test source set, which this module cannot see. `shared/data/
 * model-testing` would be the natural shared home, but it sits BELOW
 * `shared/data/core` in the dependency graph and so cannot reference
 * `PendingBillEntity`. Two copies of a fake is the lesser evil here; the
 * behaviour that matters - refusing a duplicate primary key - is asserted on the
 * real SQLite engine by `Migration134to135PendingBillsTest` regardless, so
 * neither copy is the last line of defence.
 *
 * The one behaviour that is load-bearing: [insertIfAbsent] **refuses a duplicate
 * primary key** and reports that it refused, exactly as
 * `INSERT OR IGNORE` does. A fake that appended unconditionally would make
 * `BillReminderInboxTest.theSameBillDeliveredTwiceProducesOneRow` pass for the
 * wrong reason - it would be testing that the production code checked first,
 * which is not the guarantee. The guarantee is that the database says no, and
 * this fake says no so the test can observe the refusal.
 *
 * The guarded UPDATEs ([markState], [updateAmount]) are likewise faithful: they
 * match only rows in the expected `state`, and report how many rows they changed.
 * That is what lets the "a paid bill's amount cannot be edited" case mean
 * something.
 */
@VisibleForTesting
class FakePendingBillStore : PendingBillDao, WritePendingBillDao {

    private val items = mutableListOf<PendingBillEntity>()

    /** How many inserts were attempted, including the ones SQLite would refuse. */
    var insertAttempts = 0
        private set

    /** How many of those the primary key turned away. */
    var refusedInserts = 0
        private set

    /** Every row, settled or not. */
    fun rows(): List<PendingBillEntity> = items.toList()

    // ----------------------------- reads --------------------------------

    override suspend fun findAllByState(state: String): List<PendingBillEntity> =
        items.filter { it.state == state }
            .sortedWith(compareBy({ it.dueOn }, { -it.amount }))

    override suspend fun findByOccurrenceKey(occurrenceKey: String): PendingBillEntity? =
        items.find { it.occurrenceKey == occurrenceKey }

    override suspend fun findAllByRuleId(ruleId: String): List<PendingBillEntity> =
        items.filter { it.ruleId == ruleId }
            .sortedByDescending { it.dueOn }

    override suspend fun count(): Long = items.size.toLong()

    // ----------------------------- writes -------------------------------

    /**
     * Refuses a duplicate key, as the real primary key does.
     *
     * Returns 1 when a row was written and 0 when the key was already taken, which
     * is the contract `INSERT OR IGNORE` gives and that [BillReminderInbox]
     * branches on.
     *
     * Also refuses when the existing row is a tombstone. A PAID or DISMISSED row
     * occupying the key must block a fresh PENDING insert, or a replay would
     * resurrect a bill the owner had already settled - the reason settled rows are
     * retained rather than deleted.
     */
    override suspend fun insertIfAbsent(bill: PendingBillEntity): Long {
        insertAttempts++
        if (items.any { it.occurrenceKey == bill.occurrenceKey }) {
            refusedInserts++
            return 0L
        }
        items.add(bill)
        return 1L
    }

    override suspend fun update(bill: PendingBillEntity) {
        val index = items.indexOfFirst { it.occurrenceKey == bill.occurrenceKey }
        if (index >= 0) items[index] = bill
    }

    override suspend fun markState(
        occurrenceKey: String,
        state: String,
        onlyIfState: String,
    ): Int {
        val index = items.indexOfFirst {
            it.occurrenceKey == occurrenceKey && it.state == onlyIfState
        }
        if (index < 0) return 0
        items[index] = items[index].copy(state = state)
        return 1
    }

    override suspend fun updateAmount(
        occurrenceKey: String,
        amount: Double,
        onlyIfState: String,
    ): Int {
        val index = items.indexOfFirst {
            it.occurrenceKey == occurrenceKey && it.state == onlyIfState
        }
        if (index < 0) return 0
        items[index] = items[index].copy(amount = amount)
        return 1
    }

    override suspend fun deleteAllByRuleId(ruleId: String) {
        items.removeIf { it.ruleId == ruleId }
    }

    override suspend fun deleteAll() {
        items.clear()
    }
}
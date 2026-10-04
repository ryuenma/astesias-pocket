package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivy.data.db.entity.PendingBillEntity

/**
 * Writes to the pending-bill inbox.
 *
 * Pure interface - see the note on `PendingBillDao`.
 */
@Dao
interface WritePendingBillDao {

    /**
     * Fabricates a pending bill, or does nothing if one already exists.
     *
     * `INSERT OR IGNORE` is the whole idempotency mechanism and it is why the
     * occurrence key is a derived string rather than a random UUID: the key is
     * recomputable from the rule and the month without reading anything, so a
     * scheduler re-run, a WorkManager retry, a reboot mid-write or a replayed
     * sync all arrive holding the same key and are refused by the primary key.
     *
     * The alternative - read, check whether the row is there, then insert - has a
     * window that no ordering closes, because two in-flight fabrications both
     * read "not seen yet" before either commits. That is the exact shape of the
     * bug this project has already shipped once: a PocketBase collection declared
     * `unique: true` on a field with no real index behind it, so a replayed POST
     * recorded the same transfer twice.
     *
     * Returns the number of rows actually inserted: 1 for a new bill, 0 when this
     * occurrence was already fabricated or already dealt with. Callers use it to
     * decide whether to notify, so a retry cannot produce a second notification
     * for a bill the owner has already been told about.
     *
     * `IGNORE` rather than `REPLACE` is also what makes constraint 2 hold: if the
     * owner has already adjusted the amount on this month's pending bill, a
     * replayed reminder must not overwrite that edit with the rule's amount.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(bill: PendingBillEntity): Long

    /**
     * Updates an existing pending bill in place - used when the owner adjusts the
     * amount on the fly, or deletes it.
     *
     * Never an insert: editing a pending row must not duplicate it, and the
     * primary key makes an accidental second row for this occurrence impossible
     * anyway.
     */
    @Update
    suspend fun update(bill: PendingBillEntity)

    /**
     * Settles a bill as PAID, guarded on it still being PENDING.
     *
     * The guard is what makes a replayed "pay" harmless: once the row is PAID the
     * WHERE clause matches nothing, so a second delivery cannot resurrect it into
     * the inbox or record the owner's payment twice.
     */
    @Query(
        "UPDATE pending_bills SET state = :state " +
            "WHERE occurrenceKey = :occurrenceKey AND state = :onlyIfState"
    )
    suspend fun markState(
        occurrenceKey: String,
        state: String,
        onlyIfState: String = PendingBillEntity.STATE_PENDING,
    ): Int

    /**
     * Records a new amount for an undecided bill.
     *
     * Guarded on PENDING for the same reason [markState] is: a settled bill's
     * amount is a historical fact and must not be edited by a late replay. Scoped
     * by primary key, so adjusting the amount updates that one row and cannot
     * create a second.
     */
    @Query(
        "UPDATE pending_bills SET amount = :amount " +
            "WHERE occurrenceKey = :occurrenceKey AND state = :onlyIfState"
    )
    suspend fun updateAmount(
        occurrenceKey: String,
        amount: Double,
        onlyIfState: String = PendingBillEntity.STATE_PENDING,
    ): Int

    /** Removes every tombstone for a rule. Used when the rule itself is deleted. */
    @Query("DELETE FROM pending_bills WHERE ruleId = :ruleId")
    suspend fun deleteAllByRuleId(ruleId: String)

    @Query("DELETE FROM pending_bills")
    suspend fun deleteAll()
}
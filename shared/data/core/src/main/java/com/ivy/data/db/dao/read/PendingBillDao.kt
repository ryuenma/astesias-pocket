package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.PendingBillEntity

/**
 * Reads of the pending-bill inbox.
 *
 * Pure interface: `shared/data/core` exposes Room as `implementation`, so a
 * default method here would put Room annotations on consumers' signatures and
 * break Hilt's KSP with an opaque `error.NonExistentClass`.
 */
@Dao
interface PendingBillDao {

    /**
     * What the owner is actually shown: bills reminded but not yet acted on.
     *
     * Paid and dismissed rows are retained as tombstones but filtered out here,
     * so the inbox cannot show a bill the owner has already dealt with while the
     * row still does its job of blocking a replay.
     */
    @Query(
        "SELECT * FROM pending_bills WHERE state = :state " +
            "ORDER BY dueOn ASC, amount DESC"
    )
    suspend fun findAllByState(state: String): List<PendingBillEntity>

    /**
     * One bill by its derived idempotency key.
     *
     * The lookup the reminder worker does before it fabricates anything. It is
     * an optimisation and a source of truth for "already dealt with"; the
     * primary key is what actually makes a duplicate impossible.
     */
    @Query("SELECT * FROM pending_bills WHERE occurrenceKey = :occurrenceKey")
    suspend fun findByOccurrenceKey(occurrenceKey: String): PendingBillEntity?

    /**
     * Every row for one rule, whatever its state.
     *
     * Used when a rule is edited or deleted, to see whether this month's bill
     * already exists before deciding whether to re-fabricate it.
     */
    @Query("SELECT * FROM pending_bills WHERE ruleId = :ruleId ORDER BY dueOn DESC")
    suspend fun findAllByRuleId(ruleId: String): List<PendingBillEntity>

    @Query("SELECT COUNT(*) FROM pending_bills")
    suspend fun count(): Long
}
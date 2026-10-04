package com.ivy.domain.usecase.billreminder

import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.PendingBillDao
import com.ivy.data.db.dao.write.WritePendingBillDao
import com.ivy.data.db.entity.PendingBillEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Decides whether a reminder has already been delivered for an occurrence, and
 * fabricates the pending bill when it has not.
 *
 * The idempotency argument is the important part, and it does not live here - it
 * lives in the database. See `Migration134to135_PendingBills` and
 * `WritePendingBillDao.insertIfAbsent`.
 *
 * This class computes the [PendingBillEntity.occurrenceKey], which is what makes
 * the database-level guarantee possible at all: the key must be *derivable* from
 * the rule and the month without reading anything, so a replayed delivery
 * recomputes the identical key and collides on the primary key. A random UUID
 * generated per attempt would make every retry look like a new bill, which is
 * precisely the PocketBase `unique: true` bug this project has already shipped
 * once.
 */
class BillReminderInbox @Inject constructor(
    private val pendingBillDao: PendingBillDao,
    private val writePendingBillDao: WritePendingBillDao,
) {

    /**
     * The idempotency key for one bill in one month.
     *
     * `[reminder:<ruleId>:<yyyy-MM>]` - deliberately made of the rule id and the
     * clamped due date rather than the requested day, so a rule set to the 31st
     * and clamped to 28 February keys identically however the clamp was arrived
     * at.
     *
     * A bracketed, self-describing string rather than a bare concatenation
     * because the rule id is itself a UUID containing hyphens: without the
     * separators the boundary between the two fields would depend on a fixed
     * length that a different id format would silently break, turning two
     * distinct occurrences into one key and losing a bill.
     */
    fun occurrenceKey(ruleId: UUID, dueOn: LocalDate): String =
        "reminder:[$ruleId]:[${dueOn.year}-${dueOn.monthValue.toString().padStart(2, '0')}]"

    /**
     * What happened when a reminder fired.
     */
    sealed interface Result {
        /**
         * A new pending bill was fabricated and the owner has not been told about
         * it yet.
         *
         * The caller posts a notification. Only on this path - so a retry cannot
         * re-notify for a bill already delivered.
         */
        data class Created(val bill: PendingBillEntity) : Result

        /**
         * This occurrence was already dealt with: already fabricated, already
         * paid, or already dismissed.
         *
         * No row is written and no notification is posted. This is what makes
         * constraint 3 work: the notification is a consequence of *firing*, never
         * a consequence of the owner having opened the pending row, and a
         * WorkManager retry after a successful post does not re-notify.
         */
        data object AlreadyHandled : Result
    }

    /**
     * Records that [ruleId]'s bill for [dueOn] is now due, unless it already is.
     *
     * The one and only way a pending bill comes into existence. Nothing else in
     * the codebase writes to `pending_bills`, and in particular nothing writes
     * pending items into `transactions`.
     */
    suspend fun fabricate(
        ruleId: UUID,
        dueOn: LocalDate,
        amount: Double,
        type: TransactionType,
        accountId: UUID,
        title: String?,
        description: String?,
        zone: ZoneId,
    ): Result {
        val key = occurrenceKey(ruleId, dueOn)

        // Deliberately NO read-before-write check.
        //
        // An earlier version of this method looked the key up first and returned
        // early on a hit. That was a read-then-act optimisation, and it quietly
        // turned the database into a formality: the second delivery was stopped by
        // a Kotlin `if`, so the INSERT was never attempted and nothing downstream
        // could tell the difference between "the database refused this" and "we
        // decided not to ask". It also has a race that no ordering closes - two
        // callers can both read "not seen yet" before either commits.
        //
        // So the insert is always attempted and the primary key is always the thing
        // that decides. One code path, one authority, and a caller that wants to
        // know what happened can trust the returned row count.
        val bill = PendingBillEntity(
            occurrenceKey = key,
            ruleId = ruleId.toString(),
            dueOn = BillReminderDates.dueOnInstant(dueOn, zone).toEpochMilli(),
            amount = amount,
            title = title,
            description = description,
            accountId = accountId.toString(),
            type = type,
            state = PendingBillEntity.STATE_PENDING,
        )

        // 1 = inserted, 0 = refused because the key already existed - whether the
        // existing row is still PENDING, already PAID, or already DISMISSED. The
        // refusal is the load-bearing outcome and it is decided by SQLite, not by
        // us, which is the only reason it survives a concurrent replay.
        val inserted = writePendingBillDao.insertIfAbsent(bill)
        return if (inserted > 0) Result.Created(bill) else Result.AlreadyHandled
    }

    /**
     * The owner's inbox: bills reminded about and not yet acted on.
     */
    suspend fun pendingBills(): List<PendingBillEntity> =
        pendingBillDao.findAllByState(PendingBillEntity.STATE_PENDING)

    /**
     * Adjusts a pending bill's amount on the fly.
     *
     * An `UPDATE` scoped by primary key and guarded on PENDING, so it edits the
     * existing row and cannot produce a second one - which is constraint 2. A
     * settled bill's amount is a historical fact and is left alone: returns 0
     * rather than silently rewriting history because a reminder replayed.
     *
     * @return true if the amount was changed.
     */
    suspend fun adjustAmount(occurrenceKey: String, amount: Double): Boolean {
        if (amount < 0.0) return false
        return writePendingBillDao.updateAmount(occurrenceKey, amount) > 0
    }

    /**
     * The owner dismisses a pending bill without paying it.
     *
     * Retained as a DISMISSED tombstone rather than deleted: a deletion would let
     * the next reminder for this occurrence re-create it, so a bill the owner
     * deliberately discarded would return on every replay.
     */
    suspend fun dismiss(occurrenceKey: String): Boolean =
        writePendingBillDao.markState(
            occurrenceKey = occurrenceKey,
            state = PendingBillEntity.STATE_DISMISSED,
            onlyIfState = PendingBillEntity.STATE_PENDING,
        ) > 0

    /**
     * The owner records this bill as a real transaction.
     *
     * Leaves the PAID tombstone so a replay cannot resurrect it, and returns the
     * [PendingBillEntity] for the caller to convert into a transaction. The
     * conversion itself is the owner's action and lives in the feature layer - it
     * is the only path by which a pending bill becomes money that moves, and it
     * is deliberately not reachable from the reminder worker.
     *
     * @return the paid bill, or null if it was not PENDING (already settled, or
     *   never existed).
     */
    suspend fun markPaid(occurrenceKey: String): PendingBillEntity? {
        val bill = pendingBillDao.findByOccurrenceKey(occurrenceKey)
            ?.takeIf { it.state == PendingBillEntity.STATE_PENDING }

        val settled = bill != null && writePendingBillDao.markState(
            occurrenceKey = occurrenceKey,
            state = PendingBillEntity.STATE_PAID,
            onlyIfState = PendingBillEntity.STATE_PENDING,
        ) > 0

        // Null in three cases, all of which must stop the caller writing a second
        // transaction: no such bill, it was already settled, or another caller
        // settled it between the read and the guarded UPDATE.
        return bill?.takeIf { settled }
    }

    /**
     * Every bill for a rule, settled or not.
     *
     * Used when a rule is edited or deleted, so a rule that is removed takes its
     * tombstones with it rather than leaving rows nothing will ever read.
     */
    suspend fun billsForRule(ruleId: UUID): List<PendingBillEntity> =
        pendingBillDao.findAllByRuleId(ruleId.toString())

    suspend fun deleteAllForRule(ruleId: UUID): Unit =
        writePendingBillDao.deleteAllByRuleId(ruleId.toString())
}

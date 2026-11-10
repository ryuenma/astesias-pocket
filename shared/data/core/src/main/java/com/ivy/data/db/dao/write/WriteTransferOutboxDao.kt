package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivy.data.db.entity.TransferOutboxEntity
import java.util.UUID

/**
 * Writes to the outbound transport queue.
 *
 * Pure interface - see the note on [com.ivy.data.db.dao.read.TransferOutboxDao].
 */
@Dao
interface WriteTransferOutboxDao {

    /**
     * Queues something for the server, or does nothing if it is already queued.
     *
     * `IGNORE` and the primary key are the whole idempotency mechanism, and it
     * has to be at this level rather than in Kotlin: a check-then-insert has a
     * window no ordering closes, because a retried save and a scheduled drain
     * can both read "not queued yet" before either commits. The database refuses
     * the second write instead.
     *
     * `IGNORE` rather than `REPLACE` also means a queued row is never silently
     * rewritten. If the owner edits the transfer before it drains, the row that
     * goes on the wire is the one that was recorded with the money, not a later
     * edit of it - the same reasoning that keeps an adjusted pending bill from
     * resetting to the rule's amount.
     *
     * @return 1 for a new row, 0 when this correlation id was already queued.
     *   Callers read it through [TransferOutboxWrite.wasQueued] rather than
     *   comparing to 0 inline.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueIfAbsent(item: TransferOutboxEntity): Long

    /**
     * Marks a row published, guarded on it still being PENDING.
     *
     * The guard is what makes a concurrent double-drain harmless: once the row
     * is SENT the WHERE clause matches nothing, so a second drain cannot
     * re-announce it. The row is retained rather than deleted either way - see
     * `TransferOutboxEntity` for why a tombstone beats a deletion here.
     *
     * @return rows changed: 1 marked, 0 already sent.
     */
    @Query(
        "UPDATE transfer_outbox SET state = :state, sentAt = :sentAt, lastError = NULL " +
            "WHERE correlationId = :correlationId AND state = :onlyIfState"
    )
    suspend fun markSent(
        correlationId: UUID,
        sentAt: Long,
        state: String = TransferOutboxEntity.STATE_SENT,
        onlyIfState: String = TransferOutboxEntity.STATE_PENDING,
    ): Int

    /**
     * Records a TRANSIENT failure without giving up on the row.
     *
     * Deliberately does NOT change state: a phone that recorded a transfer in a
     * lift has to publish it when it gets signal, so a failure must leave the
     * row PENDING.
     *
     * Deliberately does NOT touch [TransferOutboxEntity.attempts] either. This
     * method is for the "we do not know" class - offline, timeout, expired
     * token, sync not configured - and every one of those heals itself. Counting
     * them would walk a phone that queued transfers BEFORE signing in into a
     * terminal state on transfers that were always going to publish. Only
     * [recordRejection] counts, so the cap can only ever be reached by refusals
     * the server will actually repeat.
     *
     * Guarded on PENDING for the same reason [markSent] is: a late failure from
     * a drain that lost a race must not annotate a row another drain already
     * sent, which would leave a published transfer looking broken.
     */
    @Query(
        "UPDATE transfer_outbox SET lastError = :error " +
            "WHERE correlationId = :correlationId AND state = :onlyIfState"
    )
    suspend fun recordFailure(
        correlationId: UUID,
        error: String,
        onlyIfState: String = TransferOutboxEntity.STATE_PENDING,
    ): Int

    /**
     * Records a PERMANENT refusal: the server said no and will say no again.
     *
     * Increments [TransferOutboxEntity.attempts], which is the give-up counter
     * and nothing else, and leaves the state change to [markFailed] so the
     * decision stays in Kotlin where it is testable - a `CASE` inside the SQL
     * cannot be exercised on the JVM in this project, and an untested transition
     * is not a transition anyone should trust with money.
     */
    @Query(
        "UPDATE transfer_outbox SET attempts = attempts + 1, lastError = :error " +
            "WHERE correlationId = :correlationId AND state = :onlyIfState"
    )
    suspend fun recordRejection(
        correlationId: UUID,
        error: String,
        onlyIfState: String = TransferOutboxEntity.STATE_PENDING,
    ): Int

    /**
     * Takes a permanently refused row out of the queue, without deleting it.
     *
     * Guarded on PENDING like every other transition here, so a rejection at the
     * cap cannot overwrite a row a concurrent drain already published.
     */
    @Query(
        "UPDATE transfer_outbox SET state = :state " +
            "WHERE correlationId = :correlationId AND state = :onlyIfState"
    )
    suspend fun markFailed(
        correlationId: UUID,
        state: String = TransferOutboxEntity.STATE_FAILED,
        onlyIfState: String = TransferOutboxEntity.STATE_PENDING,
    ): Int

    /**
     * Whether this device already published this transfer.
     *
     * Used to decide whether an arriving event is ours to act on. A transfer
     * this device recorded and published comes back in the same feed as the
     * counterpart's, and treating our own event as news would put a second,
     * mirrored copy of a transfer we already recorded in her inbox.
     */
    @Query("SELECT state FROM transfer_outbox WHERE correlationId = :correlationId LIMIT 1")
    suspend fun findState(correlationId: UUID): String?

    /** Removes every row when the owner wipes all local data. */
    @Query("DELETE FROM transfer_outbox")
    suspend fun deleteAll()
}

/**
 * The one signal a queue write has to interpret, named once.
 *
 * Trivial arithmetic, and that is the point: it is the boundary between "the
 * database refused this because it already knew" and "this really happened".
 * Getting it wrong is how one transfer becomes two POSTs, so it is named here
 * rather than re-derived as a bare `== 0L` at each call site.
 */
object TransferOutboxWrite {

    /**
     * True when [inserted] means a row was genuinely queued.
     *
     * Room reports 0 for a row it refused because the primary key already
     * existed, which is precisely the replay signal.
     */
    fun wasQueued(inserted: Long): Boolean = inserted != 0L

    /**
     * True when [rowsUpdated] means a row was genuinely marked published.
     *
     * Zero means it was already SENT, i.e. another drain won.
     */
    fun wasMarkedSent(rowsUpdated: Int): Boolean = rowsUpdated > 0
}
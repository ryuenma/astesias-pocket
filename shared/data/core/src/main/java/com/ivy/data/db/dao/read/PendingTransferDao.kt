package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivy.data.db.entity.PendingTransferEntity
import java.util.UUID

/**
 * The counterpart's inbox: transfers waiting for her to confirm them.
 *
 * A separate table from `transactions`, so no balance, report, export or CSV can
 * read an unconfirmed item by accident. See
 * [com.ivy.data.db.entity.PendingTransferEntity].
 *
 * Deliberately a pure interface with no default methods: this module exposes Room
 * as `implementation`, so a default method here would put Room's annotations on
 * every consumer's signature and break Hilt's KSP step. Composition lives in the
 * repository instead.
 */
@Dao
interface PendingTransferDao {

    /**
     * Inserts an item arriving from sync. Reached only from
     * `PendingTransferRepository.onEventArrived`, which nothing calls yet.
     *
     * `IGNORE`, never `REPLACE`: a replayed delivery of a transfer she has
     * already accepted or declined must NOT overwrite that decision back to
     * PENDING. `REPLACE` would silently undo her answer on the next sync - the
     * item would reappear as unconfirmed and could be accepted a second time,
     * writing a second TRANSFER row. Ignoring is what makes a duplicate delivery
     * a true no-op.
     *
     * Returns -1 when the row already existed, which is the replay signal. Read it
     * through `PendingTransferWrite.isNewRow` rather than comparing to -1 inline.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(item: PendingTransferEntity): Long

    /**
     * State of one transfer, or null if this device has never seen it.
     *
     * This is the idempotency read: the caller consults
     * `TransferInbox.onEventArrived` with what comes back.
     */
    @Query("SELECT state FROM pending_transfers WHERE correlationId = :correlationId LIMIT 1")
    suspend fun findState(correlationId: UUID): String?

    /** Items still awaiting her decision. What the confirmation screen lists. */
    @Query("SELECT * FROM pending_transfers WHERE state = 'PENDING' ORDER BY occurredOn DESC")
    suspend fun findPending(): List<PendingTransferEntity>

    /**
     * Records her decision.
     *
     * Guarded on `state = 'PENDING'` so a double-tap cannot move an already
     * decided item. The second tap matches no row and changes nothing, which is
     * what stops one intent producing two TRANSFER rows.
     *
     * Returns the number of rows changed: 1 applied, 0 already terminal.
     */
    @Query(
        "UPDATE pending_transfers SET state = :state " +
            "WHERE correlationId = :correlationId AND state = 'PENDING'"
    )
    suspend fun markDecided(correlationId: UUID, state: String): Int

    /**
     * One item by its correlation id, or null if this device has never seen it.
     */
    @Query("SELECT * FROM pending_transfers WHERE correlationId = :correlationId LIMIT 1")
    suspend fun findByCorrelationId(correlationId: UUID): PendingTransferEntity?

    /** Removes every row when the owner wipes all local data. */
    @Query("DELETE FROM pending_transfers")
    suspend fun deleteAll()
}

/**
 * The two signals a write site has to interpret, named once.
 *
 * Both are trivial arithmetic, and that is the point: they are the boundary
 * between "the database refused this because it already knew" and "this really
 * happened". Getting either one wrong is how a duplicate gets recorded, so they
 * are named here rather than re-derived as a bare `-1 != 0` at each call site.
 */
object PendingTransferWrite {

    /**
     * True when [insertId] means a genuinely new row.
     *
     * Room reports -1 for a row it refused to insert because the primary key
     * already existed. That is precisely the replay signal: the transfer is
     * already known, so nothing was written and nothing more should be.
     */
    fun isNewRow(insertId: Long): Boolean = insertId != -1L

    /**
     * True when [rowsUpdated] means a decision was actually applied.
     *
     * Zero means the row was already terminal - the double-tap case.
     */
    fun decisionApplied(rowsUpdated: Int): Boolean = rowsUpdated > 0
}
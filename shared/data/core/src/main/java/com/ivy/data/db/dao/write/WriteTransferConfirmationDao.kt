package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivy.data.db.entity.TransferConfirmationEntity
import java.util.UUID

/**
 * Writes the counterpart's answers about transfers this device sent.
 *
 * Pure interface - see the note on [com.ivy.data.db.dao.read.TransferConfirmationDao].
 */
@Dao
interface WriteTransferConfirmationDao {

    /**
     * Records an answer, or does nothing if it is already recorded.
     *
     * `IGNORE` and the primary key are the idempotency mechanism, and it has to
     * be at this level rather than in Kotlin for the same reason it is on the
     * outbox: a check-then-insert has a window no ordering closes, because two
     * syncs - a scheduled drain and an owner opening the screen - can both read
     * "not recorded yet" before either commits.
     *
     * `IGNORE` rather than `REPLACE` is what stops the second delivery from
     * resetting [TransferConfirmationEntity.acknowledged] back to false. A
     * REPLACE would un-read every answer on every sync, so the Settings badge
     * would light up again forever and the owner would never reach a state where
     * she had seen everything.
     *
     * @return 1 for a new row, 0 when this correlation id already had an answer.
     *   Read it through [TransferConfirmationWrite.wasRecorded] rather than
     *   comparing to 0 inline.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordIfAbsent(item: TransferConfirmationEntity): Long

    /**
     * Marks one answer as read.
     *
     * Guarded on `acknowledged = 0` so re-reading the list cannot rewrite a row
     * that is already read, and so two readers racing produce one change rather
     * than two.
     *
     * @return rows changed: 1 newly acknowledged, 0 already read.
     */
    @Query(
        "UPDATE transfer_confirmations SET acknowledged = 1 " +
            "WHERE correlationId = :correlationId AND acknowledged = 0"
    )
    suspend fun acknowledge(correlationId: UUID): Int

    /**
     * Marks every recorded answer as read.
     *
     * Used when the owner opens the list: having opened it, she has seen it. Not
     * a deletion - the answers are a record of what happened to her money and
     * stay readable for as long as the row survives.
     */
    @Query("UPDATE transfer_confirmations SET acknowledged = 1 WHERE acknowledged = 0")
    suspend fun acknowledgeAll(): Int

    /** Removes every row when the owner wipes all local data. */
    @Query("DELETE FROM transfer_confirmations")
    suspend fun deleteAll()
}

/**
 * The one signal an answer write has to interpret, named once.
 *
 * Trivial arithmetic, and that is the point: it is the boundary between "the
 * database refused this because it already knew" and "this really arrived".
 * Getting it wrong is how one answer becomes a badge the owner can never clear,
 * so it is named here rather than re-derived as a bare `== 0L` at each call site.
 */
object TransferConfirmationWrite {

    /**
     * True when [inserted] means a row was genuinely recorded.
     *
     * Room reports 0 for a row it refused because the primary key already
     * existed, which is precisely the redelivery signal - not a failure.
     */
    fun wasRecorded(inserted: Long): Boolean = inserted != 0L

    /** True when [rowsUpdated] means an answer was newly marked read. */
    fun wasAcknowledged(rowsUpdated: Int): Boolean = rowsUpdated > 0
}

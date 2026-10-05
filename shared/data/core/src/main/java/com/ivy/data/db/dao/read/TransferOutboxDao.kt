package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.TransferOutboxEntity
import java.util.UUID

/**
 * Reads the outbound transport queue: what this device still owes the server.
 *
 * Deliberately a pure interface with no default methods - this module exposes
 * Room as `implementation`, so a default method would put Room's annotations on
 * every consumer's signature and break Hilt's KSP with an opaque
 * `error.NonExistentClass`.
 *
 * @see com.ivy.data.db.entity.TransferOutboxEntity for why this is a table of
 *   its own rather than a flag on `transactions`.
 */
@Dao
interface TransferOutboxDao {

    /**
     * Oldest-first, capped.
     *
     * Oldest-first so a queue that grew while the device was offline drains in
     * the order the transfers actually happened. The cap bounds one drain to a
     * fixed amount of work: a phone that has been offline for a month must not
     * spend a battery charge posting four hundred rows before the owner can do
     * anything else, and the remainder is picked up next time.
     */
    @Query(
        "SELECT * FROM transfer_outbox " +
            "WHERE state = 'PENDING' ORDER BY enqueuedAt ASC LIMIT :limit"
    )
    suspend fun findPending(limit: Int): List<TransferOutboxEntity>

    /** How much is still queued. What a "N unsent" badge would read. */
    @Query("SELECT COUNT(*) FROM transfer_outbox WHERE state = 'PENDING'")
    suspend fun countPending(): Int

    /** One row by its key, or null when this device never queued it. */
    @Query("SELECT * FROM transfer_outbox WHERE correlationId = :correlationId LIMIT 1")
    suspend fun findByCorrelationId(correlationId: UUID): TransferOutboxEntity?

    /** Whether this device already queued this transfer, whatever its state. */
    @Query("SELECT state FROM transfer_outbox WHERE correlationId = :correlationId LIMIT 1")
    suspend fun findState(correlationId: UUID): String?
}
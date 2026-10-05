package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.TransferConfirmationEntity
import com.ivy.data.db.entity.TransferConfirmationWithEvent
import java.util.UUID

/**
 * Reads what the counterpart has answered about transfers this device sent.
 *
 * Deliberately a pure interface with no default methods - this module exposes
 * Room as `implementation`, so a default method would put Room's annotations on
 * every consumer's signature and break Hilt's KSP with an opaque
 * `error.NonExistentClass`.
 *
 * @see com.ivy.data.db.entity.TransferConfirmationEntity for why this is a table
 *   of its own rather than a column on the outbox.
 */
@Dao
interface TransferConfirmationDao {

    /**
     * One answer by its key, or null when this device has never recorded one.
     *
     * The idempotency read: a redelivered answer must find the row already
     * present rather than insert a second one or reset the acknowledgement.
     */
    @Query("SELECT * FROM transfer_confirmations WHERE correlationId = :correlationId LIMIT 1")
    suspend fun findByCorrelationId(correlationId: UUID): TransferConfirmationEntity?

    /**
     * Every recorded answer, newest first.
     *
     * Newest first because the newest answer is the one she is waiting on: a
     * partner who accepts a transfer and then declines the next one should see
     * the decline at the top, not have to scroll past the earlier one.
     */
    @Query("SELECT * FROM transfer_confirmations ORDER BY recordedAt DESC")
    suspend fun findAll(): List<TransferConfirmationEntity>

    /**
     * Every answer joined to the transfer it is about, newest first.
     *
     * An INNER JOIN, and that is deliberate rather than incidental: a row here
     * without a matching retained outbox event is an answer this device cannot
     * describe - no amount, no accounts, no date - so it has nothing to render.
     * Excluding it keeps the list from showing a bare "accepted" with no
     * transfer attached, which reads as a claim about money with nothing
     * identifying it.
     *
     * Aliasing is explicit rather than relying on the joined column names: both
     * tables have a `correlationId`, and an unqualified one would be ambiguous
     * the moment the query is edited.
     */
    @Query(
        "SELECT c.correlationId AS correlationId, " +
            "c.decision AS decision, " +
            "c.decidedBy AS decidedBy, " +
            "c.recordedAt AS recordedAt, " +
            "c.acknowledged AS acknowledged, " +
            "e.amount AS amount, " +
            "e.currency AS currency, " +
            "e.occurredOn AS occurredOn, " +
            "e.fromAccountName AS fromAccountName, " +
            "e.toAccountName AS toAccountName " +
            "FROM transfer_confirmations c " +
            "INNER JOIN transfer_outbox e ON e.correlationId = c.correlationId " +
            "WHERE e.kind = 'TRANSFER_EVENT' " +
            "ORDER BY c.recordedAt DESC"
    )
    suspend fun findAllWithEvent(): List<TransferConfirmationWithEvent>

    /**
     * How many answers the owner has not read yet. Drives the Settings badge.
     *
     * Counted, not merely checked, so the row can say how many. A count that
     * disagrees with the list would be worse than no badge at all.
     */
    @Query("SELECT COUNT(*) FROM transfer_confirmations WHERE acknowledged = 0")
    suspend fun countUnacknowledged(): Int
}

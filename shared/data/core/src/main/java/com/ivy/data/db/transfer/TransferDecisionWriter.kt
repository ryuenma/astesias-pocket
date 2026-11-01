package com.ivy.data.db.transfer

import androidx.room.withTransaction
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.dao.read.PendingTransferDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.TransactionEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a decision and its ledger row as ONE database write.
 *
 * The two writes used to be separate statements issued from the domain layer: mark
 * the inbox row ACCEPTED, then insert the TRANSFER leg. Nothing tied them together,
 * so a ledger insert that threw left the transfer ACCEPTED with no row behind it -
 * and because `findPending` only returns PENDING rows, the transfer could never be
 * acted on again. Her money moved, the app said "accepted", and nothing appeared
 * anywhere. That is unrecoverable by design, not by accident.
 *
 * This lives in `data/core` because the transaction primitive comes from Room, and
 * Room is an implementation dependency here rather than part of the module's API -
 * `shared/domain` cannot name it. The state machine stays in the domain; only the
 * atomic pairing lives here.
 */
@Singleton
class TransferDecisionWriter @Inject constructor(
    private val db: IvyRoomDatabase,
    private val pendingDao: PendingTransferDao,
    private val transactionDao: WriteTransactionDao,
) {

    /**
     * @param leg the ledger row to write, or null when the decision writes none
     *   (declining leaves the owner's leg untouched - the money already moved).
     * @return false when the inbox row was no longer PENDING, meaning another tap
     *   already applied a decision and this call wrote nothing at all.
     */
    suspend fun decideAndWrite(
        correlationId: UUID,
        state: String,
        leg: TransactionEntity?,
    ): Boolean = db.withTransaction {
        val rows = pendingDao.markDecided(
            correlationId = correlationId,
            state = state,
        )
        if (rows == 0) {
            // Lost the race against a concurrent tap. Roll back rather than
            // continue: writing the leg now would double it.
            return@withTransaction false
        }
        if (leg != null) {
            transactionDao.save(leg)
        }
        true
    }
}

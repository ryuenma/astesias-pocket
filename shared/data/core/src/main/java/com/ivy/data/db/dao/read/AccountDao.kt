package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.AccountEntity
import java.util.*

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isDeleted = :deleted ORDER BY orderNum ASC")
    suspend fun findAll(deleted: Boolean = false): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: UUID): AccountEntity?

    /**
     * The live account with exactly this name, or null.
     *
     * Exists for one caller: an arriving cross-profile transfer names its
     * destination by name rather than by id, because the sender's account id does
     * not exist in this database. See `TransferEventSync`.
     *
     * Exact match rather than LIKE or a case-insensitive comparison, on purpose.
     * Two accounts called "Cash" is an ordinary state for one person, and
     * picking one of them would put money into an account the transfer never
     * named. Refusing to guess beats a coin-flip about someone's savings.
     * Deleted accounts are excluded: a transfer cannot arrive into one the owner
     * has closed.
     */
    @Query("SELECT * FROM accounts WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun findByName(name: String): AccountEntity?

    @Query("SELECT MAX(orderNum) FROM accounts")
    suspend fun findMaxOrderNum(): Double?
}

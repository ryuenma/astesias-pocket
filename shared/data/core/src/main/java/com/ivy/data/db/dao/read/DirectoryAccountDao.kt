package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivy.data.db.entity.DirectoryAccountEntity

/**
 * Other profiles' accounts, published so a transfer can name a destination.
 *
 * Contains no balances and no transactions - see [DirectoryAccountEntity].
 */
@Dao
interface DirectoryAccountDao {

    /** Accounts belonging to any profile except [ownProfileId]. */
    @Query("SELECT * FROM directory_accounts WHERE profileId != :ownProfileId ORDER BY profileName, name")
    suspend fun findOthers(ownProfileId: String): List<DirectoryAccountEntity>

    @Query("SELECT * FROM directory_accounts WHERE profileId = :profileId ORDER BY name")
    suspend fun findByProfile(profileId: String): List<DirectoryAccountEntity>

    @Query("SELECT * FROM directory_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun findByAccountId(accountId: String): DirectoryAccountEntity?

    /**
     * Replaces one profile's published accounts with the current set.
     *
     * Delete-then-insert rather than upsert: an account she deleted must DISAPPEAR
     * from my list, and an upsert-only sync would leave it selectable forever. The
     * whole set is rewritten inside one transaction so a partial sync can never
     * leave me with half her accounts.
     */
    @androidx.room.Transaction
    suspend fun replaceProfile(profileId: String, accounts: List<DirectoryAccountEntity>) {
        deleteProfile(profileId)
        if (accounts.isNotEmpty()) insertAll(accounts)
    }

    @Query("DELETE FROM directory_accounts WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<DirectoryAccountEntity>)

    @Query("DELETE FROM directory_accounts")
    suspend fun deleteAll()
}

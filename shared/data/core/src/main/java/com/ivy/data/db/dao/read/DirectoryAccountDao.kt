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
 *
 * Deliberately a pure interface: composing the set ([replaceProfile]) lives in
 * [com.ivy.data.profile.DirectoryRepository]. A default method here would drag
 * Room's annotations into every consumer's signature, and this module exposes
 * Room as `implementation`, so the annotation is not on a consumer's classpath.
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

    @Query("DELETE FROM directory_accounts WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<DirectoryAccountEntity>)

    @Query("DELETE FROM directory_accounts")
    suspend fun deleteAll()
}

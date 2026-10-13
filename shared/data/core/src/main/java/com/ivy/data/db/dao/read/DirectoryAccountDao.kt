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

    /**
     * The account [accountId] as published by [profileId].
     *
     * Both keys, because [accountId] is only unique within one profile: two
     * profiles legitimately publish the same id (see [DirectoryAccountEntity]),
     * so a lookup by account id alone cannot say whose account it found. The
     * former `LIMIT 1` over the non-unique column returned an arbitrary
     * profile's row and called it an answer.
     */
    @Query("SELECT * FROM directory_accounts WHERE profileId = :profileId AND accountId = :accountId")
    suspend fun findByAccountId(profileId: String, accountId: String): DirectoryAccountEntity?

    @Query("DELETE FROM directory_accounts WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<DirectoryAccountEntity>)

    @Query("DELETE FROM directory_accounts")
    suspend fun deleteAll()
}

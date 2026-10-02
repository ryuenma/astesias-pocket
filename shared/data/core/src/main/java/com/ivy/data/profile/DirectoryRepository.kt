package com.ivy.data.profile

import com.ivy.data.db.dao.read.DirectoryAccountDao
import com.ivy.data.db.entity.DirectoryAccountEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the published account directory.
 *
 * Write-side composition (replacing one profile's accounts wholesale) is
 * deliberately absent: nothing writes here until sync exists, and taking
 * `IvyRoomDatabase` just to call `withTransaction` would drag Room's
 * `RoomDatabase` into every consumer's injected signature while this module
 * exposes Room as `implementation` - which Hilt's KSP step cannot resolve.
 * The sync layer adds the transactional write when there is a writer for it.
 */
@Singleton
class DirectoryRepository @Inject constructor(
    private val directoryAccountDao: DirectoryAccountDao,
) {
    /** Accounts published by other profiles, excluding my own. */
    suspend fun others(ownProfileId: ProfileId): List<DirectoryAccountEntity> =
        directoryAccountDao.findOthers(ownProfileId.value)
}

package com.ivy.data.profile

import com.ivy.data.db.dao.read.DirectoryAccountDao
import com.ivy.data.db.entity.DirectoryAccountEntity
import com.ivy.data.sync.SyncConfig
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
    private val config: SyncConfig,
) {
    /**
     * Accounts published by other profiles, excluding this device's own.
     *
     * "My own" is resolved HERE, from [SyncConfig], and is deliberately not a
     * caller parameter any more. It used to be one, typed [ProfileId] - the
     * LOCAL profile identity - and every caller passed `ProfileId.DEFAULT`,
     * whose value is the string `"default"`. This table is keyed by SYNC profile
     * ids (`"owner"`, `"so"`), so the two spaces never intersect: `findOthers`
     * compared every row against `"default"`, matched none of them, and the
     * destination picker offered this device its own accounts as places to send
     * money. Both are non-empty strings, so nothing at the call site looked
     * wrong and nothing failed to compile.
     *
     * Taking the id from the config that owns it removes the possibility of
     * passing an id from the wrong space, rather than documenting against it.
     *
     * Unpaired devices get an empty list: sync has never populated this table,
     * and rows surviving from an earlier pairing belong to a device identity
     * that no longer exists, so none of them are a legitimate destination.
     */
    suspend fun others(): List<DirectoryAccountEntity> {
        val own = config.profileId ?: return emptyList()
        return directoryAccountDao.findOthers(own)
    }
}

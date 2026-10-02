package com.ivy.data.sync

import com.ivy.data.db.dao.read.DirectoryAccountDao
import com.ivy.data.db.entity.DirectoryAccountEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Pulls the shared account directory into the local cache.
 *
 * This is the writer that makes the transfer picker's empty state become real.
 * Until it runs, the picker honestly reports that no other profile's accounts
 * have arrived; after it runs, it lists them.
 *
 * Refresh is replace-per-profile rather than wholesale: an account she renamed
 * should update in place, and an account she deleted server-side should
 * disappear, without ever touching rows belonging to profiles this device did
 * not just refresh.
 */
@Singleton
class DirectorySyncRepository @Inject constructor(
    private val api: DirectorySyncApi,
    private val dao: DirectoryAccountDao,
    private val config: SyncConfig,
) {

    /**
     * Refresh the directory cache.
     *
     * @return true when the cache was actually updated. False means sync is not
     *   configured or the server was unreachable - both are ordinary, and
     *   neither is an error worth surfacing to the user mid-transaction.
     */
    suspend fun refresh(): Boolean {
        if (!config.isConfigured) {
            Timber.i("Sync not configured; directory cache left as-is")
            return false
        }

        val envelope = api.fetchDirectory() ?: return false
        val remote = envelope.items
        val names = api.fetchProfiles()
            ?.items
            ?.associate { profile -> profile.profileId to profile.displayName }
            .orEmpty()

        // A row we cannot identify is worse than a missing row: a malformed id
        // would make the picker offer a destination nobody can reconcile.
        val parsed = remote.mapNotNull { row ->
            val uuid = runCatching { UUID.fromString(row.localAccountId) }.getOrNull()
            if (uuid == null) {
                Timber.w("Dropping directory row with unparseable accountId")
                return@mapNotNull null
            }
            if (row.profileId.isBlank() || row.accountName.isBlank()) return@mapNotNull null
            DirectoryAccountEntity(
                accountId = uuid,
                profileId = row.profileId,
                profileName = names[row.profileId] ?: row.profileId,
                name = row.accountName,
                currencyCode = row.currency.ifBlank { null },
            )
        }

        val profilesPresent = parsed.map { it.profileId }.toSet()
        // Only clear profiles we actually refreshed. Clearing every row on a
        // partial response would blank a working picker because one request
        // came back short.
        for (profileId in profilesPresent) {
            dao.deleteProfile(profileId)
        }
        if (parsed.isNotEmpty()) {
            dao.insertAll(parsed)
        }

        Timber.i("Directory refreshed: ${parsed.size} account(s) across ${profilesPresent.size} profile(s)")
        return true
    }
}
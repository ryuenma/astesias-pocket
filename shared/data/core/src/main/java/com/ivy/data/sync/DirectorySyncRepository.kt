package com.ivy.data.sync

import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.DirectoryAccountDao
import com.ivy.data.db.entity.DirectoryAccountEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * The shared account directory, in both directions.
 *
 * ## Why this exists as a write path at all
 *
 * The picker reads this collection and nothing ever wrote to it. A client that
 * only pulls a shared resource it also has to populate shows an empty list
 * forever, and every green signal says it works: the build passes, the refresh
 * succeeds against an empty collection, the screen renders an honest empty
 * state. The empty state was the last piece built and the only one testable
 * before the transport existed, which is exactly how it got mistaken for the
 * feature.
 *
 * ## Refresh is replace-per-profile, never wholesale
 *
 * Only for profiles actually present in the response. Clearing everything on a
 * short response would blank a working picker because one request came back
 * incomplete.
 *
 * ## Publishing is per-account and never deletes
 *
 * One POST per changed account, so one refused row cannot cost the others their
 * publish. The server's `deleteRule` is null by design - a device deleting its
 * own row is explicitly not wanted - so a locally deleted account keeps its
 * directory row rather than the row being withdrawn.
 */
@Singleton
class DirectorySyncRepository @Inject constructor(
    private val api: DirectorySyncApi,
    private val dao: DirectoryAccountDao,
    private val accounts: AccountDao,
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

    /**
     * What one publish pass achieved.
     *
     * Counted rather than a boolean because a partial publish is the normal
     * case on a phone: three accounts go up and one is refused. A boolean
     * cannot tell that apart from "all four went up", and a caller that only
     * sees a boolean will treat a half-published directory as a done one.
     */
    data class PublishReport(
        /** Rows the server created. */
        val created: Int = 0,
        /** Existing rows this device renamed or re-currency'd. */
        val updated: Int = 0,
        /** Already correct on the server; deliberately not written again. */
        val unchanged: Int = 0,
        /** We do not know whether these landed. */
        val undelivered: Int = 0,
    ) {
        /** True when at least one account could not be confirmed published. */
        val hasFailures: Boolean get() = undelivered > 0
    }

    /**
     * Publish this device's OWN accounts, reconciling against what the server
     * already holds.
     *
     * ## Why reconcile rather than POST everything
     *
     * The server holds a unique index on (profileId, localAccountId), so a
     * re-POST of an account the server already has is refused. That refusal is
     * reported as [DirectorySyncApi.PublishResult.AlreadyPublished] - a
     * success - but it also means a naive POST-everything loop can NEVER
     * propagate a rename: the create is refused before the name is ever
     * compared. So the pass first reads what is published, then PATCHes the
     * rows whose name or currency actually differ.
     *
     * That read is also what keeps this cheap: an account already correct is
     * skipped entirely rather than written, so a pass that finds nothing to do
     * issues zero writes.
     *
     * ## Degradation, stated rather than hidden
     *
     * If the listing fails, every account is POSTed anyway. A duplicate is
     * refused by the index and counted as unchanged, so nothing is corrupted
     * and no new account is lost - only the renames wait for the next pass.
     * The alternative, refusing to publish anything because a read failed,
     * would mean a flaky GET keeps a genuinely new account out of the other
     * phone's picker forever.
     *
     * @return a count per outcome. Never throws: an account is already saved
     *   locally by the time this runs, and this must not take down the
     *   coroutine that saved it.
     */
    suspend fun publishLocalAccounts(): PublishReport {
        if (!config.isConfigured) {
            Timber.i("Sync not configured; nothing published")
            return PublishReport()
        }

        val mine = config.profileId
        val local = accounts.findAll(deleted = false)
        if (local.isEmpty()) {
            Timber.i("No local accounts to publish")
            return PublishReport()
        }

        // Only THIS profile's rows are ever written or patched. The server
        // enforces the same thing via createRule/updateRule, but a client that
        // filtered by profileId anyway cannot be the source of a cross-profile
        // write if that rule is ever changed.
        val published = api.fetchDirectory()?.items
            ?.filter { row -> row.profileId == mine }
            .orEmpty()
            .associateBy { row -> row.localAccountId }

        var created = 0
        var updated = 0
        var unchanged = 0
        var undelivered = 0

        for (account in local) {
            val localId = account.id.toString()
            // A blank name is refused by the server's required `accountName`,
            // and a row the picker cannot label is worse than no row: it offers
            // an unidentifiable destination. Skipped, not published.
            if (account.name.isBlank()) {
                Timber.w("Skipping unnamed account $localId")
                continue
            }

            val currency = account.currency.orEmpty()
            val existing = published[localId]

            // The row exists and already says this. Writing again would be
            // refused by the index, so not writing is both correct and cheaper.
            //
            // Deliberately NOT conditioned on a record id: this account needs
            // no write, so it needs no address. Demanding one here would
            // classify a perfectly correct row as work to do, and then fail to
            // do it - the account would be counted undelivered forever while
            // its name sat correct on the server.
            if (existing != null &&
                existing.accountName == account.name &&
                existing.currency == currency
            ) {
                unchanged++
                continue
            }

            // Per-account isolation around the WRITE ITSELF, not around the
            // result: one account that throws must not cost the other three
            // their publish, and the account is already committed to Room by
            // the time this runs, so an exception escaping would take down the
            // screen that saved it. [DirectorySyncApi] is specified never to
            // throw; this catch is for anything it did not anticipate.
            val counted = runCatching {
                when {
                    // No server-side id to address, so a rename cannot be
                    // delivered; posting would not deliver it either, because
                    // the unique index refuses the duplicate.
                    existing != null && existing.recordId.isBlank() ->
                        DirectorySyncApi.PublishResult.Undelivered("no record id")

                    existing == null ->
                        api.publishAccount(localId, account.name, currency)

                    else -> api.updateAccount(existing.recordId, account.name, currency)
                }
            }.getOrElse { error ->
                Timber.e(error, "Could not publish account $localId")
                DirectorySyncApi.PublishResult.Undelivered(error::class.java.simpleName)
            }

            when (counted) {
                DirectorySyncApi.PublishResult.Accepted ->
                    if (existing == null) created++ else updated++

                DirectorySyncApi.PublishResult.AlreadyPublished -> unchanged++

                is DirectorySyncApi.PublishResult.Undelivered -> {
                    Timber.w("Account $localId not published: ${counted.reason}")
                    undelivered++
                }
            }
        }

        val report = PublishReport(created, updated, unchanged, undelivered)
        Timber.i(
            "Published local accounts: $created created, $updated updated, " +
                "$unchanged unchanged, $undelivered undelivered",
        )
        return report
    }
}
package com.ivy.data.sync

import com.ivy.data.db.dao.read.DirectoryAccountDao
import com.ivy.data.db.entity.DirectoryAccountEntity
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

/**
 * The regression the destination picker shipped with: a refresh that fetched
 * BOTH profiles' rows left the device holding only one profile's accounts.
 *
 * ## Why a fake DAO, and what makes it honest
 *
 * The bug was not in [DirectorySyncRepository] - the fetch was complete and the
 * log said `Directory refreshed: 30 account(s) across 2 profile(s)`. It was in
 * what SQLite then did with 30 rows written into a table keyed on `accountId`
 * alone, with `OnConflictStrategy.REPLACE` as the insert strategy.
 *
 * So the fake below is not a stub returning a fixed list. It IS the database: it
 * holds rows in a map keyed on `(profileId, accountId)`, and `insertAll`
 * implements REPLACE by overwriting that key - the key
 * [DirectoryAccountEntity] now declares, and the one the server's unique index
 * has always used. Under the old single-column key this same 30-row batch
 * collapses to 21 rows, all `qa_a`.
 *
 * ## What this does NOT prove, stated plainly
 *
 * This proves the refresh path is correct GIVEN a composite key. It cannot prove
 * the entity declares one - Room's `@Entity` is `RetentionPolicy.CLASS`, so it is
 * not readable by reflection at test runtime. That half is pinned separately:
 *
 * - `DirectoryAccountEntityKeyTest` reads the schema Room actually exported for
 *   this version, which is generated FROM the entity at build time, so a
 *   regression to a single-column key turns that test red.
 * - `Migration137to138Test` (androidTest) runs `runMigrationsAndValidate` against
 *   real SQLite, so a migration that does not produce the entity's shape is
 *   refused by Room itself.
 *
 * Together those three cover key, schema and engine. The assertion here is a ROW
 * COUNT PER PROFILE after a refresh, never the shape of the query or the contents
 * of the batch handed to `insertAll`: both of those were already correct at the
 * time of the bug. Only the stored state was wrong.
 */
@Suppress("ClassNaming")
class DirectoryAccountProfileKeyTest {

    /**
     * A DAO whose storage is keyed the way the table is now keyed, with
     * `insertAll` doing what `OnConflictStrategy.REPLACE` does: last writer wins
     * on a key collision.
     *
     * Deliberately NOT a fake that returns a fixed list. A stub would have passed
     * throughout the original bug, because the repository was never wrong.
     */
    private class KeyedDao : DirectoryAccountDao {
        val rows = LinkedHashMap<Pair<String, UUID>, DirectoryAccountEntity>()

        override suspend fun findOthers(ownProfileId: String): List<DirectoryAccountEntity> =
            rows.values.filter { it.profileId != ownProfileId }

        override suspend fun findByProfile(profileId: String): List<DirectoryAccountEntity> =
            rows.values.filter { it.profileId == profileId }

        override suspend fun findByAccountId(
            profileId: String,
            accountId: String,
        ): DirectoryAccountEntity? =
            rows.values.firstOrNull {
                it.profileId == profileId && it.accountId.toString() == accountId
            }

        override suspend fun deleteProfile(profileId: String) {
            rows.keys.retainAll { it.first != profileId }
        }

        override suspend fun insertAll(accounts: List<DirectoryAccountEntity>) {
            accounts.forEach { rows[it.profileId to it.accountId] = it }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }

        /** Count per profile - the measurement that exposed the bug. */
        fun countPerProfile(): Map<String, Int> =
            rows.values.groupingBy { it.profileId }.eachCount()
    }

    // The real qa_a/qa_b shape, scaled down but structurally identical: the two
    // profiles share nine account ids, which is what a couple sees when one
    // phone's data was set up from the other's backup.
    //
    // Derived from a counter rather than written out, and padded to a fixed
    // width, so every id is a well-formed UUID of the same length. A template
    // like "...00000000000$it" silently produces a 12-hex-group last segment for
    // a two-digit index, and UUID.fromString throws "UUID string too large" in
    // the constructor - which fails every test in the class and says nothing
    // about the key.
    private fun accountId(n: Int): UUID =
        UUID.fromString("00000000-0000-4000-8000-%012d".format(n))

    private val sharedIds: List<UUID> = (1..9).map { accountId(it) }

    private val mineOnlyIds: List<UUID> = (10..21).map { accountId(it) }

    /** `qa_a` publishes 21 accounts; `qa_b` publishes 9, all of them shared. */
    private val serverRows: List<DirectorySyncApi.RemoteDirectoryAccount> =
        (sharedIds + mineOnlyIds).map { id ->
            DirectorySyncApi.RemoteDirectoryAccount(
                localAccountId = id.toString(),
                profileId = "qa_a",
                accountName = "Mine $id",
                currency = "IDR",
            )
        } + sharedIds.map { id ->
            DirectorySyncApi.RemoteDirectoryAccount(
                localAccountId = id.toString(),
                profileId = "qa_b",
                accountName = "Hers $id",
                currency = "IDR",
            )
        }

    private fun profiles() = DirectorySyncApi.PbList(
        listOf(
            DirectorySyncApi.RemoteProfile("qa_a", "QA A"),
            DirectorySyncApi.RemoteProfile("qa_b", "QA B"),
        ),
        2,
    )

    private fun repoWith(
        dao: KeyedDao,
        rows: List<DirectorySyncApi.RemoteDirectoryAccount> = serverRows,
    ) = DirectorySyncRepository(
        api = mockk<DirectorySyncApi>().also {
            coEvery { it.fetchDirectory() } returns DirectorySyncApi.PbList(rows, rows.size)
            coEvery { it.fetchProfiles() } returns profiles()
        },
        dao = dao,
        accounts = mockk(relaxed = true),
        config = mockk<SyncConfig>().also { coEvery { it.isConfigured } returns true },
    )

    @Test
    fun `every published account survives a refresh, under both profiles`() = runTest {
        val dao = KeyedDao()

        repoWith(dao).refresh() shouldBe true

        // The measurement that exposed the bug, stated as the invariant it broke:
        // 30 fetched, 30 stored, and BOTH profiles represented. Under the old
        // single-column key this read `{qa_a=21}` - qa_b's nine rows had been
        // REPLACEd away by the nine ids the two profiles shared.
        dao.countPerProfile() shouldBe mapOf("qa_a" to 21, "qa_b" to 9)
        dao.rows.size shouldBe 30
    }

    @Test
    fun `the shared account id exists once per profile, not once overall`() = runTest {
        val dao = KeyedDao()
        val shared = sharedIds.first()

        repoWith(dao).refresh() shouldBe true

        // THE assertion. One id, two profiles, two rows: each profile's own name
        // for the same account id. A table keyed on accountId alone can only hold
        // one of them, which is precisely why the partner's copy vanished.
        dao.findByProfile("qa_a").map { it.accountId }.toSet() shouldBe
            (sharedIds + mineOnlyIds).toSet()
        dao.findByProfile("qa_b").map { it.accountId }.toSet() shouldBe sharedIds.toSet()

        dao.findByAccountId("qa_a", shared.toString())!!.name shouldBe "Mine $shared"
        dao.findByAccountId("qa_b", shared.toString())!!.name shouldBe "Hers $shared"
    }

    @Test
    fun `the picker can see the other profile once the rows are actually kept`() = runTest {
        val dao = KeyedDao()

        repoWith(dao).refresh() shouldBe true

        // The user-visible consequence, asserted at the query the picker makes.
        // "No other profiles yet" was this list being empty while the sync log
        // reported a healthy 30-account refresh.
        dao.findOthers("qa_a").size shouldBe 9
        dao.findOthers("qa_a").all { it.profileId == "qa_b" } shouldBe true
    }

    @Test
    fun `a second refresh re-syncs in place instead of accumulating rows`() = runTest {
        val dao = KeyedDao()
        val repo = repoWith(dao)

        repo.refresh() shouldBe true
        repo.refresh() shouldBe true

        // The composite key is what keeps a re-sync idempotent: one row per
        // (profile, account), not one per refresh. `refresh` deletes each present
        // profile and re-inserts, so a key that forgot `profileId` would make two
        // profiles delete each other's rows mid-pass.
        dao.rows.size shouldBe 30
        dao.countPerProfile() shouldBe mapOf("qa_a" to 21, "qa_b" to 9)
    }

    @Test
    fun `a rename updates the one row it belongs to and no other`() = runTest {
        val dao = KeyedDao()
        val shared = sharedIds.first()
        repoWith(dao).refresh() shouldBe true

        // The partner renames her account; the server now says the new name.
        val renamed = DirectorySyncApi.RemoteDirectoryAccount(
            localAccountId = shared.toString(),
            profileId = "qa_b",
            accountName = "Renamed",
            currency = "IDR",
        )
        val rows = serverRows.filterNot {
            it.profileId == "qa_b" && it.localAccountId == shared.toString()
        } + renamed

        repoWith(dao, rows).refresh() shouldBe true

        // REPLACE on a composite key is the intended upsert: her row moves to the
        // new name, my same-id row is untouched, and the count is unchanged.
        dao.findByAccountId("qa_b", shared.toString())!!.name shouldBe "Renamed"
        dao.findByAccountId("qa_a", shared.toString())!!.name shouldBe "Mine $shared"
        dao.rows.size shouldBe 30
    }
}

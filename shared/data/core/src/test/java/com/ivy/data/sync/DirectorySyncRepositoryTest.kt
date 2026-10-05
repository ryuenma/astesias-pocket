package com.ivy.data.sync

import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.DirectoryAccountDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.DirectoryAccountEntity
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timber.log.Timber
import java.util.UUID

/**
 * Proves the invariants DirectorySyncRepository's comments claim.
 *
 * A comment asserting "only refreshed profiles are cleared" is worth nothing
 * unless something fails when it stops being true, so each rule below is a test.
 */
class DirectorySyncRepositoryTest {

    private val dao: DirectoryAccountDao = mockk(relaxed = true)
    private val api: DirectorySyncApi = mockk()
    private val config: SyncConfig = mockk()
    private val accountDao: AccountDao = mockk(relaxed = true)

    private val validId = "9a1c6b0e-1111-4a2b-9c3d-000000000001"

    /** The id [localAccounts] gives its first account, as text on the wire. */
    private val localId = "00000000-0000-4000-8000-000000000000"

    /** The local accounts a publish pass would read. */
    private fun localAccounts(vararg names: String) =
        names.mapIndexed { index, name ->
            AccountEntity(
                name = name,
                currency = "IDR",
                color = 0,
                id = UUID.fromString("00000000-0000-4000-8000-00000000000$index"),
            )
        }

    private fun profiles(vararg pairs: Pair<String, String>) =
        DirectorySyncApi.PbList(
            items = pairs.map { (id, name) ->
                DirectorySyncApi.RemoteProfile(profileId = id, displayName = name)
            },
            totalItems = pairs.size
        )

    private fun remote(
        id: String = validId,
        profileId: String = "so",
        name: String = "Partner Bank",
        currency: String = "IDR"
    ) = DirectorySyncApi.RemoteDirectoryAccount(
        localAccountId = id,
        profileId = profileId,
        accountName = name,
        currency = currency
    )

    private fun repo() = DirectorySyncRepository(api, dao, accountDao, config)

    @Test
    fun `does nothing at all when sync is not configured`() = runTest {
        coEvery { config.isConfigured } returns false

        repo().refresh() shouldBe false

        // Not even a read: an unconfigured device must not be able to mistake a
        // missing token for an empty server.
        coVerify(exactly = 0) { api.fetchDirectory() }
        coVerify(exactly = 0) { dao.insertAll(any()) }
        coVerify(exactly = 0) { dao.deleteProfile(any()) }
    }

    @Test
    fun `reports no update when the server cannot be reached`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchDirectory() } returns null

        repo().refresh() shouldBe false

        coVerify(exactly = 0) { dao.deleteProfile(any()) }
    }

    @Test
    fun `maps a published account into the local cache`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchDirectory() } returns
            DirectorySyncApi.PbList(listOf(remote()), 1)
        coEvery { api.fetchProfiles() } returns profiles("so" to "Partner")

        repo().refresh() shouldBe true

        val written = slot<List<DirectoryAccountEntity>>()
        coVerify { dao.insertAll(capture(written)) }
        written.captured.single().let {
            it.accountId shouldBe UUID.fromString(validId)
            it.profileId shouldBe "so"
            it.name shouldBe "Partner Bank"
            it.currencyCode shouldBe "IDR"
            // The owner sees WHOSE account this is, not just an opaque id.
            it.profileName shouldBe "Partner"
        }
    }

    @Test
    fun `drops a row whose account id cannot be parsed`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchDirectory() } returns
            DirectorySyncApi.PbList(listOf(remote(id = "not-a-uuid")), 1)
        coEvery { api.fetchProfiles() } returns profiles("so" to "Partner")

        repo().refresh() shouldBe true

        // A malformed id would offer a destination nobody can reconcile, which
        // is worse than the row simply not being there.
        //
        // insertAll is not merely called with an empty list - it is never called,
        // so there is no pointless write against the database at all.
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `falls back to the profile id when the name is unknown`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchDirectory() } returns
            DirectorySyncApi.PbList(listOf(remote(profileId = "mystery")), 1)
        // Profiles call failed or came back empty.
        coEvery { api.fetchProfiles() } returns null

        repo().refresh() shouldBe true

        val written = slot<List<DirectoryAccountEntity>>()
        coVerify { dao.insertAll(capture(written)) }
        // Showing the raw id beats showing a blank owner.
        written.captured.single().profileName shouldBe "mystery"
    }

    @Test
    fun `clears only the profiles that were actually refreshed`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchDirectory() } returns
            DirectorySyncApi.PbList(listOf(remote(profileId = "so")), 1)
        coEvery { api.fetchProfiles() } returns profiles("so" to "Partner")

        repo().refresh() shouldBe true

        // The owner's own rows must survive a refresh that only saw 'so'.
        coVerify(exactly = 1) { dao.deleteProfile("so") }
        coVerify(exactly = 0) { dao.deleteProfile("owner") }
    }

    @Test
    fun `clears nothing when the response is empty`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), 0)
        coEvery { api.fetchProfiles() } returns profiles("so" to "Partner")

        repo().refresh() shouldBe true

        // An empty response must never wipe a working picker.
        coVerify(exactly = 0) { dao.deleteProfile(any()) }
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `does not write an empty batch`() = runTest {
        coEvery { config.isConfigured } returns true
        // Every row was malformed, so there is nothing to insert.
        coEvery { api.fetchDirectory() } returns
            DirectorySyncApi.PbList(listOf(remote(id = "bad")), 1)
        coEvery { api.fetchProfiles() } returns profiles("so" to "Partner")

        repo().refresh() shouldBe true

        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `blank currency becomes null rather than an empty string`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchDirectory() } returns
            DirectorySyncApi.PbList(listOf(remote(currency = "")), 1)
        coEvery { api.fetchProfiles() } returns profiles("so" to "Partner")

        repo().refresh() shouldBe true

        val written = slot<List<DirectoryAccountEntity>>()
        coVerify { dao.insertAll(capture(written)) }
        // An empty currency string would render as a blank currency chip.
        written.captured.single().currencyCode shouldBe null
    }

    // -----------------------------------------------------------------
    // Publishing: the direction that did not exist at all.
    //
    // The client could read directory_accounts and nothing ever wrote to it, so
    // the picker was permanently empty with "No other profiles yet". Every test
    // below fails if publishing is removed.
    // -----------------------------------------------------------------

    @Test
    fun `publishes a local account the server does not have`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank")
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), 0)
        coEvery { api.publishAccount(any(), any(), any()) } returns
            DirectorySyncApi.PublishResult.Accepted

        val report = repo().publishLocalAccounts()

        report.created shouldBe 1
        report.updated shouldBe 0
        report.undelivered shouldBe 0
        report.hasFailures shouldBe false
    }

    @Test
    fun `publishes the account id, name and currency that identify it`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank")
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), 0)
        coEvery { api.publishAccount(any(), any(), any()) } returns
            DirectorySyncApi.PublishResult.Accepted

        repo().publishLocalAccounts()

        // The local Room id is the join key the other device resolves names
        // against, so a wrong value here makes the row unresolvable rather than
        // merely mislabelled.
        coVerify { api.publishAccount("00000000-0000-4000-8000-000000000000", "Bank", "IDR") }
    }

    @Test
    fun `never publishes anything when sync is not configured`() = runTest {
        coEvery { config.isConfigured } returns false

        repo().publishLocalAccounts() shouldBe DirectorySyncRepository.PublishReport()

        // Not even a read: an unconfigured device has no profileId, and a body
        // without one is refused by the server's createRule anyway.
        coVerify(exactly = 0) { api.fetchDirectory() }
        coVerify(exactly = 0) { api.publishAccount(any(), any(), any()) }
        coVerify(exactly = 0) { api.updateAccount(any(), any(), any()) }
    }

    @Test
    fun `writes nothing when there are no local accounts`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns emptyList()
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), 0)

        repo().publishLocalAccounts() shouldBe DirectorySyncRepository.PublishReport()

        coVerify(exactly = 0) { api.publishAccount(any(), any(), any()) }
    }

    @Test
    fun `does not rewrite an account the server already has correct`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank")
        // Same name, same currency: the row already says this.
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(
            listOf(remote(id = localId, profileId = "owner", name = "Bank", currency = "IDR")),
            1,
        )

        val report = repo().publishLocalAccounts()

        report.unchanged shouldBe 1
        // Both write verbs. A create is refused by the unique index and an
        // update of a correct row is pure noise on every app start.
        coVerify(exactly = 0) { api.publishAccount(any(), any(), any()) }
        coVerify(exactly = 0) { api.updateAccount(any(), any(), any()) }
    }

    @Test
    fun `updates a renamed account instead of failing to create a duplicate`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank Baru")
        // The server still holds the OLD name, under a PocketBase record id.
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(
            listOf(
                DirectorySyncApi.RemoteDirectoryAccount(
                    recordId = "pbrec123",
                    localAccountId = "00000000-0000-4000-8000-000000000000",
                    profileId = "owner",
                    accountName = "Bank",
                    currency = "IDR",
                )
            ),
            1,
        )
        coEvery { api.updateAccount(any(), any(), any()) } returns
            DirectorySyncApi.PublishResult.Accepted

        val report = repo().publishLocalAccounts()

        report.updated shouldBe 1
        report.created shouldBe 0
        // THE case a create-everything loop cannot do: the unique index refuses
        // the create before the name is ever compared, so the other phone would
        // keep showing "Bank" for ever.
        coVerify(exactly = 0) { api.publishAccount(any(), any(), any()) }
        coVerify { api.updateAccount("pbrec123", "Bank Baru", "IDR") }
    }

    @Test
    fun `counts a refused duplicate as unchanged rather than a failure`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank")
        // The listing failed, so we POST blindly and the index refuses.
        coEvery { api.fetchDirectory() } returns null
        coEvery { api.publishAccount(any(), any(), any()) } returns
            DirectorySyncApi.PublishResult.AlreadyPublished

        val report = repo().publishLocalAccounts()

        // AlreadyPublished is a success: the index refused precisely because
        // one correct row exists. Reporting it as undelivered would make every
        // app start look like a failure.
        report.unchanged shouldBe 1
        report.hasFailures shouldBe false
    }

    @Test
    fun `still creates when the listing could not be read`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Baru")
        coEvery { api.fetchDirectory() } returns null
        coEvery { api.publishAccount(any(), any(), any()) } returns
            DirectorySyncApi.PublishResult.Accepted

        val report = repo().publishLocalAccounts()

        // Refusing to publish because a GET failed would keep a genuinely new
        // account out of the other phone's picker for ever.
        report.created shouldBe 1
        coVerify { api.publishAccount(any(), any(), any()) }
    }

    @Test
    fun `counts an undeliverable account instead of claiming it published`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank")
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), 0)
        coEvery { api.publishAccount(any(), any(), any()) } returns
            DirectorySyncApi.PublishResult.Undelivered("timeout")

        val report = repo().publishLocalAccounts()

        report.created shouldBe 0
        report.undelivered shouldBe 1
        // A partial publish must be visible. A caller that only sees a boolean
        // treats a half-published directory as a done one.
        report.hasFailures shouldBe true
    }

    @Test
    fun `one failed account does not cost the others their publish`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Good", "Bad", "AlsoGood")
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), 0)
        coEvery { api.publishAccount(any(), any(), any()) } answers {
            if (secondArg<String>() == "Bad") {
                DirectorySyncApi.PublishResult.Undelivered("HTTP 403")
            } else {
                DirectorySyncApi.PublishResult.Accepted
            }
        }

        val report = repo().publishLocalAccounts()

        report.created shouldBe 2
        report.undelivered shouldBe 1
    }

    @Test
    fun `skips an account with no name rather than publishing an unlabelled row`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("   ")
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), 0)

        repo().publishLocalAccounts()

        // The server requires a non-blank accountName, so this would be refused
        // anyway - and a row the picker cannot label is worse than no row.
        coVerify(exactly = 0) { api.publishAccount(any(), any(), any()) }
    }

    @Test
    fun `never patches a row belonging to another profile`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank")
        // Another profile happens to publish under the SAME local account id.
        // Publishing it would inject a fake destination into our own account
        // list; the server's createRule would refuse the create, but the update
        // rule would let us rewrite THEIR row.
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(
            listOf(
                DirectorySyncApi.RemoteDirectoryAccount(
                    recordId = "pbrecOTHER",
                    localAccountId = "00000000-0000-4000-8000-000000000000",
                    profileId = "so",
                    accountName = "Their Bank",
                    currency = "IDR",
                )
            ),
            1,
        )
        coEvery { api.publishAccount(any(), any(), any()) } returns
            DirectorySyncApi.PublishResult.Accepted

        val report = repo().publishLocalAccounts()

        report.created shouldBe 1
        // Their row must be invisible to us, not merely un-updated.
        coVerify(exactly = 0) { api.updateAccount("pbrecOTHER", any(), any()) }
    }

    @Test
    fun `a correct row needs no server id, because it needs no write`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank")
        // No PocketBase id at all - and that must not matter, because there is
        // nothing to send. Treating "no id" as "work to do" would then fail to
        // do the work and count a correct account as undelivered for ever.
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(
            listOf(remote(id = localId, profileId = "owner", name = "Bank", currency = "IDR")),
            1,
        )

        val report = repo().publishLocalAccounts()

        report.unchanged shouldBe 1
        report.undelivered shouldBe 0
        coVerify(exactly = 0) { api.publishAccount(any(), any(), any()) }
        coVerify(exactly = 0) { api.updateAccount(any(), any(), any()) }
    }

    @Test
    fun `reports a rename it has no server id for instead of silently dropping it`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank Baru")
        // The row exists but came back with no PocketBase id, so it cannot be
        // addressed. A create would be refused by the unique index, so the
        // rename cannot be delivered by any route.
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(
            listOf(remote(id = localId, profileId = "owner", name = "Bank")),
            1,
        )

        val report = repo().publishLocalAccounts()

        report.undelivered shouldBe 1
        report.hasFailures shouldBe true
        coVerify(exactly = 0) { api.updateAccount(any(), any(), any()) }
    }

    @Test
    fun `publishing never throws into the coroutine that saved the account`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { accountDao.findAll(false) } returns localAccounts("Bank")
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), 0)
        coEvery { api.publishAccount(any(), any(), any()) } throws
            IllegalStateException("socket died")

        // The account is already in Room. An exception escaping here would take
        // down the screen that saved it.
        repo().publishLocalAccounts().undelivered shouldBe 1
    }

    @Test
    fun `touches no Timber tree that does not exist`() {
        // Timber with no tree planted is a no-op; refresh() must not assume a
        // logger is installed, or a unit test run would crash on Timber.e.
        Timber.e(Exception("boom"), "logging with no tree planted")
    }
}
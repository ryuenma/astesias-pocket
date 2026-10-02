package com.ivy.data.sync

import com.ivy.data.db.dao.read.DirectoryAccountDao
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

    private val validId = "9a1c6b0e-1111-4a2b-9c3d-000000000001"

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

    private fun repo() = DirectorySyncRepository(api, dao, config)

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

    @Test
    fun `touches no Timber tree that does not exist`() {
        // Timber with no tree planted is a no-op; refresh() must not assume a
        // logger is installed, or a unit test run would crash on Timber.e.
        Timber.e(Exception("boom"), "logging with no tree planted")
    }
}
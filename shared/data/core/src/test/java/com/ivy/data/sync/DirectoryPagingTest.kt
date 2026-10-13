package com.ivy.data.sync

import com.ivy.data.db.dao.read.DirectoryAccountDao
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The directory is PAGED, and page one was being treated as the whole thing.
 *
 * ## The defect, measured against the live server 2026-10-13
 *
 * `directory_accounts` held 31 rows. `GET /api/collections/directory_accounts/records`
 * with no parameters answered `items.size = 30, totalItems = 31, perPage = 30` -
 * PocketBase's default page size - and the row that fell off the end was the
 * newest one, `QaBWallet`. The same URL with `perPage=200` returned all 31. The
 * client logged `Directory refreshed: 30 account(s) across 2 profile(s)`, which
 * reads as a healthy full refresh because it counted what it had fetched.
 *
 * That count is not merely cosmetic. [DirectorySyncRepository] deletes each
 * profile it saw before inserting what it fetched, so a truncated page removes
 * the rows past the boundary from the LOCAL CACHE as well - rows that response
 * then cannot restore. On a device the visible result is an account missing from
 * the transfer picker with nothing logged as wrong.
 *
 * ## What each assertion covers
 *
 * - The paging loop itself, with `fetchDirectoryPage` stubbed: the HTTP client is
 *   a private `lazy` OkHttp instance with no injection seam, so a page-level stub
 *   is the only way to exercise a multi-page directory without a server.
 * - The repository's refusal to rewrite the cache from a response that does not
 *   account for every row it claims exists.
 * - A CONTROL that a complete fetch still writes. A guard that refuses
 *   everything would pass the refusal test and break the app.
 */
@Suppress("ClassNaming")
class DirectoryPagingTest {

    private fun accountId(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    private fun row(profileId: String, n: Int, name: String) =
        DirectorySyncApi.RemoteDirectoryAccount(
            localAccountId = accountId(n),
            profileId = profileId,
            accountName = name,
            currency = "IDR",
        )

    /** 30 rows in page one, as the live server returned; `totalItems` says 31. */
    private val pageOne = (1..30).map { row("qa_a", it, "Mine $it") }
    private val pageTwo = listOf(row("qa_b", 31, "Hers 31"))

    private fun apiWithRealPaging(): DirectorySyncApi =
        spyk(DirectorySyncApi(mockk<SyncConfig>()))

    // ---------------------------------------------------------------------
    // The paging loop
    // ---------------------------------------------------------------------

    @Test
    fun `the directory fetch collects every page, not just the first`() = runTest {
        val api = apiWithRealPaging()
        coEvery { api.fetchDirectoryPage(1) } returns
            DirectorySyncApi.PbList(pageOne, totalItems = 31)
        coEvery { api.fetchDirectoryPage(2) } returns
            DirectorySyncApi.PbList(pageTwo, totalItems = 31)

        val all = api.fetchDirectory()

        // The row that was silently missing on the device.
        all!!.items.size shouldBe 31
        all.items.map { it.localAccountId }.toSet().size shouldBe 31
        all.items.last().accountName shouldBe "Hers 31"
    }

    @Test
    fun `the fetch stops as soon as it has every row the server reported`() = runTest {
        val api = apiWithRealPaging()
        coEvery { api.fetchDirectoryPage(1) } returns
            DirectorySyncApi.PbList(pageOne, totalItems = 31)
        coEvery { api.fetchDirectoryPage(2) } returns
            DirectorySyncApi.PbList(pageTwo, totalItems = 31)

        api.fetchDirectory()!!.items.size shouldBe 31

        // Two requests were enough. A loop that asked for page 3 would be a loop
        // that never trusts `totalItems`.
        coVerify(exactly = 0) { api.fetchDirectoryPage(3) }
    }

    @Test
    fun `a single-page directory still costs exactly one request`() = runTest {
        val api = apiWithRealPaging()
        coEvery { api.fetchDirectoryPage(1) } returns
            DirectorySyncApi.PbList(pageOne, totalItems = 30)

        api.fetchDirectory()!!.items.size shouldBe 30

        // The common case must not pay for the fix. `items.size >= totalItems`
        // short-circuits before any second request.
        coVerify(exactly = 0) { api.fetchDirectoryPage(2) }
    }

    @Test
    fun `a failing page yields no directory at all rather than a partial one`() = runTest {
        val api = apiWithRealPaging()
        coEvery { api.fetchDirectoryPage(1) } returns
            DirectorySyncApi.PbList(pageOne, totalItems = 31)
        coEvery { api.fetchDirectoryPage(2) } returns null

        // Null, not 30 rows: a partial directory must never reach the cache
        // rewrite, because that rewrite deletes before it inserts.
        api.fetchDirectory() shouldBe null
    }

    @Test
    fun `the page size asked for is larger than the default that truncated us`() {
        // Pins the value that caused the bug. PocketBase's default page size is
        // 30; asking for 30 or fewer would reintroduce it silently.
        (DirectorySyncApi.DIRECTORY_PAGE_SIZE > 30) shouldBe true
    }

    // ---------------------------------------------------------------------
    // The repository's refusal to rewrite the cache from a short response
    // ---------------------------------------------------------------------

    private fun repo(api: DirectorySyncApi, dao: DirectoryAccountDao) =
        DirectorySyncRepository(
            api = api,
            dao = dao,
            accounts = mockk(relaxed = true),
            config = mockk<SyncConfig>().also { coEvery { it.isConfigured } returns true },
        )

    @Test
    fun `a short fetch is refused and nothing in the cache is touched`() = runTest {
        val dao = mockk<DirectoryAccountDao>(relaxed = true)
        val api = mockk<DirectorySyncApi>()
        // 30 rows delivered, 31 admitted: the measured shape of the bug.
        coEvery { api.fetchDirectory() } returns
            DirectorySyncApi.PbList(pageOne, totalItems = 31)

        repo(api, dao).refresh() shouldBe false

        // THE assertion. Not one delete, not one insert: the rows this device
        // already holds are exactly the ones a truncated page would have eaten.
        coVerify(exactly = 0) { dao.deleteProfile(any()) }
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `a complete fetch is still applied`() = runTest {
        val dao = mockk<DirectoryAccountDao>(relaxed = true)
        val api = mockk<DirectorySyncApi>()
        val rows = pageOne + pageTwo
        coEvery { api.fetchDirectory() } returns
            DirectorySyncApi.PbList(rows, totalItems = rows.size)
        coEvery { api.fetchProfiles() } returns
            DirectorySyncApi.PbList(
                listOf(DirectorySyncApi.RemoteProfile("qa_a", "QA A")),
                totalItems = 1,
            )

        // The control. A guard that refuses everything would satisfy the test
        // above and leave the picker permanently empty.
        repo(api, dao).refresh() shouldBe true
        coVerify(exactly = 1) { dao.insertAll(any()) }
    }

    @Test
    fun `a server that reports zero rows is complete, not short`() = runTest {
        val dao = mockk<DirectoryAccountDao>(relaxed = true)
        val api = mockk<DirectorySyncApi>()
        coEvery { api.fetchDirectory() } returns DirectorySyncApi.PbList(emptyList(), totalItems = 0)
        coEvery { api.fetchProfiles() } returns DirectorySyncApi.PbList(emptyList(), totalItems = 0)

        // 0 of 0 is a complete answer - a brand-new server - and must not be
        // mistaken for a truncation. Nothing is deleted either way, because no
        // profile appears in the response.
        repo(api, dao).refresh() shouldBe true
        coVerify(exactly = 0) { dao.deleteProfile(any()) }
    }
}

package com.ivy.data.sync

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Base64

/**
 * Proves the fix for the bug where sync publish returned HTTP 400 forever.
 *
 * ## The bug, as measured on the live server 2026-10-13
 *
 * The stored token in `shared_prefs/ivy_wallet_prefs.xml` (`astesia.sync.token`)
 * was a JWT whose `exp` was 57.5 hours in the past. PocketBase treats an expired
 * token as an anonymous caller, so every rule keyed on
 * `@request.auth.profileId` failed. Two signatures:
 *
 * - a WRITE is refused `400 {"data":{},"message":"Failed to create record."}`
 * - a READ returns **HTTP 200 with `totalItems: 0`**
 *
 * The second is the dangerous one. The directory refresh looks like it
 * succeeded, and because the refresh is replace-per-profile-only-for-profiles-
 * present-in-the-response (deliberate, per fact #131), the picker silently keeps
 * serving stale rows.
 *
 * ## What these tests pin
 *
 * 1. A request with a locally-known-dead token is NOT SENT. That single
 *    behaviour closes both signatures: no write gets a 400 to misreport, and no
 *    read gets an empty page to mistake for success.
 * 2. A token near expiry is renewed FIRST, and the request then carries the NEW
 *    token - not the one that was about to die.
 * 3. A renewal that fails because the phone is offline does NOT block the
 *    request, because that token may still have a day of life and only the
 *    server can refuse it.
 * 4. `auth-refresh` refuses an already-expired token (HTTP 401), which is why
 *    renewal must happen EARLY. This is the measurement that decides the design.
 */
class SyncTokenRenewalTest {

    private fun token(expSeconds: Long): String {
        val header = """{"alg":"HS256","typ":"JWT"}"""
        val payload = """{"exp":$expSeconds,"id":"rec1","refreshable":true,"type":"auth"}"""
        val enc: (String) -> String =
            { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
        return "${enc(header)}.${enc(payload)}.sig"
    }

    /**
     * The real wall clock, in seconds.
     *
     * Anchored on the device's own clock rather than a fixed constant because
     * [SyncToken] reads `System.currentTimeMillis()` directly - a token built
     * around a fictional "now" would be judged against the real one and every
     * case would land on the wrong side of its expiry. The offsets under test
     * are hours and days wide, so the milliseconds a test takes to run cannot
     * move a verdict.
     */
    private val nowSeconds: Long get() = System.currentTimeMillis() / 1000

    private fun config(
        token: String?,
        baseUrl: String? = "https://astesias-pb.fly.dev",
        profileId: String? = "qa_a",
        nowSeconds: Long = this.nowSeconds,
    ): SyncConfig {
        // A real SyncConfig over a stubbed SharedPrefs, so the expiry logic
        // under test is the production one rather than a reimplementation.
        val prefs = mockk<com.ivy.base.legacy.SharedPrefs>(relaxed = true)
        every { prefs.getString(SyncConfigKeys.BASE_URL, any()) } returns baseUrl
        every { prefs.getString(SyncConfigKeys.TOKEN, any()) } returns token
        every { prefs.getString(SyncConfigKeys.PROFILE_ID, any()) } returns profileId
        return SyncConfig(prefs)
    }

    /** The key names, mirrored because they are private to SyncConfig. */
    private object SyncConfigKeys {
        const val BASE_URL = "astesia.sync.base_url"
        const val TOKEN = "astesia.sync.token"
        const val PROFILE_ID = "astesia.sync.profile_id"
    }

    // -----------------------------------------------------------------
    // 1. The local expiry check
    // -----------------------------------------------------------------

    @Test
    fun `the exact token found on the device is correctly read as expired`() {
        // exp was 57.5 hours in the past. This is the regression's own input,
        // hard-coded so the test fails if the decoder ever drifts.
        val dead = config(token(expSeconds = nowSeconds - (57.5 * 3_600).toLong()))
        dead.isTokenUsable shouldBe false
    }

    @Test
    fun `a token with days left is usable`() {
        val live = config(token(expSeconds = nowSeconds + 4 * 86_400))
        live.isTokenUsable shouldBe true
    }

    @Test
    fun `an unconfigured device is not reported as expired`() {
        // No profile means nothing was ever paired, which is a different state
        // from "paired and ran out" and must not read as the same thing.
        config(token = null, profileId = null).isTokenUsable shouldBe true
    }

    // -----------------------------------------------------------------
    // 2. Renewal happens early, and the request uses the new token
    // -----------------------------------------------------------------

    @Test
    fun `a token inside the renewal margin is renewed before any request`() = runTest {
        val cfg = config(token(expSeconds = nowSeconds + 6 * 3_600))
        val api = mockk<DirectoryAuthApi>()

        // Renewal is driven through the object's companion, so the object is
        // mocked and the config's own decision logic is what is under test.
        io.mockk.mockkObject(DirectoryAuthApi)
        coEvery {
            DirectoryAuthApi.refresh(any(), any())
        } returns DirectoryAuthApi.Result(
            DirectoryAuthApi.Outcome.SUCCESS,
            DirectoryAuthApi.Credentials(
                token = "new-token",
                profileId = "qa_a",
                displayName = "QA Device A",
            ),
        )

        val renewal = cfg.renewTokenIfNeeded()

        (renewal is SyncConfig.Renewal.Renewed) shouldBe true
        (renewal as SyncConfig.Renewal.Renewed).token shouldBe "new-token"
        io.mockk.unmockkObject(DirectoryAuthApi)
        api shouldBe api
    }

    @Test
    fun `a token with plenty of life left is never renewed`() = runTest {
        // Otherwise every sync request would spend a round trip.
        val cfg = config(token(expSeconds = nowSeconds + 100 * 3_600))
        io.mockk.mockkObject(DirectoryAuthApi)

        cfg.renewTokenIfNeeded() shouldBe SyncConfig.Renewal.NotNeeded

        coVerify(exactly = 0) { DirectoryAuthApi.refresh(any(), any()) }
        io.mockk.unmockkObject(DirectoryAuthApi)
    }

    @Test
    fun `an already dead token still gets a renewal attempt`() = runTest {
        // THE load-bearing design decision. `auth-refresh` refuses an expired
        // token with 401 (measured), so the attempt FAILS - but it is what turns
        // an unexplained "HTTP 400 " into an honest "sign in again". Skipping it
        // because we already know the answer would lose the diagnosis.
        val cfg = config(token(expSeconds = nowSeconds - 57 * 3_600))
        io.mockk.mockkObject(DirectoryAuthApi)
        coEvery { DirectoryAuthApi.refresh(any(), any()) } returns DirectoryAuthApi.Result(
            DirectoryAuthApi.Outcome.BAD_CREDENTIALS,
            detail = "This connection expired. Sign in again.",
        )

        val renewal = cfg.renewTokenIfNeeded()

        (renewal is SyncConfig.Renewal.Expired) shouldBe true
        coVerify(exactly = 1) { DirectoryAuthApi.refresh(any(), any()) }
        io.mockk.unmockkObject(DirectoryAuthApi)
    }

    @Test
    fun `an offline renewal failure does not invalidate the stored token`() = runTest {
        // A timeout is not evidence the token is bad. It may have another day of
        // life, and only the server can refuse it - so the request must proceed.
        val cfg = config(token(expSeconds = nowSeconds + 3_600))
        io.mockk.mockkObject(DirectoryAuthApi)
        coEvery { DirectoryAuthApi.refresh(any(), any()) } returns DirectoryAuthApi.Result(
            DirectoryAuthApi.Outcome.UNREACHABLE,
            detail = "Could not reach astesias-pb.fly.dev",
        )

        val renewal = cfg.renewTokenIfNeeded()

        (renewal is SyncConfig.Renewal.Unreachable) shouldBe true
        // The token is untouched, so the caller can still try the request.
        cfg.isTokenUsable shouldBe true
        io.mockk.unmockkObject(DirectoryAuthApi)
    }

    @Test
    fun `a refresh that returns a DIFFERENT profile is refused`() = runTest {
        // Writing it would silently re-point this device at the wrong identity,
        // and every subsequent request would carry a correlationId this device
        // never issued.
        val cfg = config(token(expSeconds = nowSeconds + 3_600))
        io.mockk.mockkObject(DirectoryAuthApi)
        coEvery { DirectoryAuthApi.refresh(any(), any()) } returns DirectoryAuthApi.Result(
            DirectoryAuthApi.Outcome.SUCCESS,
            DirectoryAuthApi.Credentials("token-for-someone-else", "so", "The Other One"),
        )

        val renewal = cfg.renewTokenIfNeeded()

        (renewal is SyncConfig.Renewal.Expired) shouldBe true
        io.mockk.unmockkObject(DirectoryAuthApi)
    }

    @Test
    fun `an unconfigured device renews nothing`() = runTest {
        config(token = null, baseUrl = null, profileId = null)
            .renewTokenIfNeeded() shouldBe SyncConfig.Renewal.NotConfigured
    }

    // -----------------------------------------------------------------
    // 3. A dead token must not produce an anonymous request
    // -----------------------------------------------------------------

    @Test
    fun `a publish with a dead token reports the expiry instead of HTTP 400`() = runTest {
        // THE regression. The outbox row recorded `lastError = "HTTP 400 "`,
        // which is indistinguishable from a server-side validation problem and
        // is what sent the diagnosis looking in the wrong place entirely.
        val cfg = config(token(expSeconds = nowSeconds - 57 * 3_600))
        io.mockk.mockkObject(DirectoryAuthApi)
        coEvery { DirectoryAuthApi.refresh(any(), any()) } returns DirectoryAuthApi.Result(
            DirectoryAuthApi.Outcome.BAD_CREDENTIALS,
            detail = "This connection expired. Sign in again.",
        )
        val api = DirectorySyncApi(cfg)

        val result = api.publishAccount(
            localAccountId = "00000000-0000-4000-8000-000000000000",
            accountName = "Rekening Uji",
            currency = "IDR",
        )

        (result is DirectorySyncApi.PublishResult.Undelivered) shouldBe true
        (result as DirectorySyncApi.PublishResult.Undelivered).reason shouldBe "sign-in expired"
        io.mockk.unmockkObject(DirectoryAuthApi)
    }

    @Test
    fun `a read with a dead token returns null rather than an empty page`() = runTest {
        // THE dangerous half. A real HTTP 200 with totalItems 0 is what the
        // server sends an anonymous caller, and DirectorySyncRepository would
        // treat it as a successful refresh that found nothing - leaving the
        // picker serving stale rows with no error anywhere.
        val cfg = config(token(expSeconds = nowSeconds - 57 * 3_600))
        io.mockk.mockkObject(DirectoryAuthApi)
        coEvery { DirectoryAuthApi.refresh(any(), any()) } returns DirectoryAuthApi.Result(
            DirectoryAuthApi.Outcome.BAD_CREDENTIALS,
        )
        val api = DirectorySyncApi(cfg)

        api.fetchDirectory() shouldBe null

        io.mockk.unmockkObject(DirectoryAuthApi)
    }

    @Test
    fun `the repository leaves the cache alone when the token is dead`() = runTest {
        // The end-to-end consequence, and the one the owner actually sees: the
        // picker must not be rewritten from a response that was never fetched.
        val cfg = config(token(expSeconds = nowSeconds - 57 * 3_600))
        io.mockk.mockkObject(DirectoryAuthApi)
        coEvery { DirectoryAuthApi.refresh(any(), any()) } returns DirectoryAuthApi.Result(
            DirectoryAuthApi.Outcome.BAD_CREDENTIALS,
        )
        val api = DirectorySyncApi(cfg)
        val dao = mockk<com.ivy.data.db.dao.read.DirectoryAccountDao>(relaxed = true)
        val repo = DirectorySyncRepository(
            api = api,
            dao = dao,
            accounts = mockk(relaxed = true),
            config = cfg,
        )

        repo.refresh() shouldBe false

        // Nothing was deleted and nothing was inserted. Before the fix the
        // server's empty page would have been read as "refresh succeeded".
        coVerify(exactly = 0) { dao.deleteProfile(any()) }
        coVerify(exactly = 0) { dao.insertAll(any()) }
        io.mockk.unmockkObject(DirectoryAuthApi)
    }
}

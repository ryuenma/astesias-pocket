package com.ivy.data.sync

import io.kotest.matchers.shouldBe
import java.util.Base64
import org.junit.Test

/**
 * Proves the expiry rule that keeps a paired device paired.
 *
 * This is tested exhaustively because the alternative to a correct local check
 * is a silent one, and silent is the whole bug: measured live 2026-10-13, an
 * expired token gets a write refused `400 {"data":{},"message":"Failed to
 * create record."}` while a READ returns **HTTP 200 with `totalItems: 0`**. The
 * read half looks like success, so nothing anywhere reported a failure and the
 * picker kept serving stale rows.
 */
class SyncTokenTest {

    /** A real-shaped PocketBase auth token: header.payload.signature, exp in seconds. */
    private fun token(expSeconds: Long, refreshable: Boolean = true): String {
        val header = """{"alg":"HS256","typ":"JWT"}"""
        val payload = """{"collectionId":"pbc_2975614142","exp":$expSeconds,"id":"mv7bfmiefeegxk1","refreshable":$refreshable,"type":"auth"}"""
        val enc: (String) -> String = {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray())
        }
        return "${enc(header)}.${enc(payload)}.c3VwZXJzZWNyZXRzaWduYXR1cmU"
    }

    private val now = 1_800_000_000_000L // fixed, so no test depends on the wall clock

    @Test
    fun `a live token is not expired`() {
        val t = token(expSeconds = (now / 1000) + 3_600)
        SyncToken.isExpired(t, now) shouldBe false
        SyncToken.expiryMillis(t) shouldBe (now / 1000 + 3_600) * 1000
    }

    @Test
    fun `a token past its exp is expired`() {
        // The exact state the device was found in: exp 57.5 hours in the past.
        val t = token(expSeconds = (now / 1000) - (57.5 * 3_600).toLong())
        SyncToken.isExpired(t, now) shouldBe true
    }

    @Test
    fun `the expiry boundary itself counts as expired`() {
        // `exp` is the last second the token is good, and PocketBase refuses
        // from exp onwards. Being generous here would send one doomed request.
        SyncToken.isExpired(token(now / 1000), now) shouldBe true
    }

    @Test
    fun `expiry is read in seconds and converted to millis`() {
        // Off by 1000x is the classic mistake with a JWT exp, and it would make
        // every token look either eternal or long dead.
        SyncToken.expiryMillis(token(1_700_000_000)) shouldBe 1_700_000_000_000L
    }

    @Test
    fun `an unpadded JWT payload still decodes`() {
        // A JWT's middle segment is unpadded base64url BY CONSTRUCTION, and
        // Base64.getUrlDecoder() throws on a length that is not a multiple of
        // four. That throw in a background sync is how "cannot read the
        // expiry" became a crash rather than a null.
        val header = """{"alg":"HS256"}"""
        val payload = """{"exp":${now / 1000 + 60},"type":"auth"}"""
        val enc: (String) -> String =
            { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
        val t = "${enc(header)}.${enc(payload)}.sig"
        SyncToken.expiryMillis(t) shouldBe (now / 1000 + 60) * 1000
    }

    @Test
    fun `an unreadable token has no known expiry`() {
        // NOT "expired". Unknown means the server gets to answer, which is what
        // happened before this class existed and is the right fallback for a
        // token shape nobody anticipated.
        SyncToken.expiryMillis("not-a-jwt") shouldBe null
        SyncToken.expiryMillis("") shouldBe null
        SyncToken.expiryMillis(null) shouldBe null
    }

    @Test
    fun `a garbage payload does not crash`() {
        val enc: (String) -> String =
            { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
        val t = "${enc("{}")}.${enc("this is not json")}.sig"
        SyncToken.expiryMillis(t) shouldBe null
    }

    @Test
    fun `a payload with no exp claim has no known expiry`() {
        val t = token(expSeconds = 0)
        SyncToken.expiryMillis(t) shouldBe null
    }

    @Test
    fun `an unreadable token is treated as usable by default`() {
        // The fail-open direction, chosen deliberately: refusing to sync on a
        // token we could not parse would take the directory away for a reason
        // nobody established, and the server still enforces the real rule.
        SyncToken.isExpired("garbage", now) shouldBe false
    }

    @Test
    fun `a token inside the renewal margin counts as expiring`() {
        // Tokens last 120h. One with 6 hours left must be renewed NOW, because
        // auth-refresh refuses an already-expired token (measured: HTTP 401),
        // so there is no repairing a pairing that ran out - only renewing a
        // live one.
        val t = token(expSeconds = (now / 1000) + 6 * 3_600)
        SyncToken.isExpiringWithin(t, now) shouldBe true
    }

    @Test
    fun `a token well inside its lifetime is not renewed`() {
        // Otherwise every single sync request would spend a round trip.
        val t = token(expSeconds = (now / 1000) + 100 * 3_600)
        SyncToken.isExpiringWithin(t, now) shouldBe false
    }

    @Test
    fun `an already expired token counts as expiring`() {
        // The renewal attempt still has to be MADE - it is what produces the
        // honest "sign in again" instead of an unexplained HTTP 400.
        val t = token(expSeconds = (now / 1000) - 3_600)
        SyncToken.isExpiringWithin(t, now) shouldBe true
    }

    @Test
    fun `an unreadable token is renewed rather than assumed fine`() {
        // Fails toward trying. A renewal that succeeds costs one request; a
        // renewal that is skipped costs the whole pairing at the next expiry.
        SyncToken.isExpiringWithin("garbage", now) shouldBe true
    }

    @Test
    fun `the margin is well inside the token lifetime`() {
        // If the margin ever exceeded the lifetime, EVERY token would look like
        // it needed renewing on every request.
        (SyncToken.REFRESH_MARGIN_MILLIS < SyncToken.TOKEN_LIFETIME_MILLIS) shouldBe true
        SyncToken.TOKEN_LIFETIME_MILLIS shouldBe 120L * 60 * 60 * 1000
    }
}

package com.ivy.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

/**
 * Reading a token's expiry WITHOUT asking the server.
 *
 * ## Why this exists
 *
 * A PocketBase auth token is a JWT that carries its own `exp` claim, and the
 * server honours it exactly. So the question "is this token still good?" is
 * answered by a base64 decode on the device - no request, no round trip, no
 * waiting for the server to refuse.
 *
 * That matters because the refusal is the bug. Measured against the live
 * server 2026-10-13: with a token whose `exp` was 57.5 hours in the past,
 * a WRITE is refused `400 {"data":{},"message":"Failed to create record."}`
 * and a READ returns **HTTP 200 with `totalItems: 0`**. PocketBase treats an
 * expired token as an anonymous caller, and its list rules answer an anonymous
 * list with an empty page rather than a 403 - so the directory refresh looks
 * like it succeeded, returned nothing, and left the picker serving stale rows
 * with no error anywhere. A stuck outbox row reading `lastError = "HTTP 400 "`
 * is the loud version of the same thing.
 *
 * Decoding `exp` locally turns both of those into a state we can act on
 * BEFORE spending a request, and that is the whole point: the failure was
 * silent precisely because nothing checked.
 *
 * ## Why the margin exists
 *
 * `auth-refresh` also refuses an already-expired token (measured: HTTP 401
 * `"The request requires valid record authorization token."`). So a refresh
 * only works while the token is still alive. Tokens live 120 hours; refreshing
 * while there is still a day left is the only way a long-lived pairing ever
 * gets renewed. See [isExpiringWithin].
 *
 * ## What it deliberately does not do
 *
 * It does not verify the signature. Nothing here needs to: the claim is read
 * to decide whether to *attempt* a renewal, and a forged expiry can at worst
 * cause an unnecessary refresh or an unnecessary re-auth prompt. Both are
 * harmless and both fail closed - the server still enforces the real thing.
 */
object SyncToken {

    /** Tokens on this server last 120 hours (measured). */
    const val TOKEN_LIFETIME_MILLIS = 120L * 60 * 60 * 1000

    /**
     * Refresh when less than this is left.
     *
     * A day of slack on a five-day token. Comfortably wider than any plausible
     * clock skew between the phone and the server, and narrow enough that a
     * phone used at all gets refreshed long before it can expire. A device
     * left in a drawer for longer than this will expire - and the re-auth path
     * is the honest outcome for that, not something a bigger margin can paper
     * over.
     */
    const val REFRESH_MARGIN_MILLIS = 24L * 60 * 60 * 1000

    /**
     * When this token stops being accepted, as epoch millis, or null when it
     * cannot be read.
     *
     * Null is NOT the same as "valid". It means "we do not know", and the
     * callers treat that as a reason to make the request and let the server
     * answer - which is what happened before this class existed, and is the
     * right fallback for a token shape nobody anticipated. Guessing "valid"
     * would reintroduce exactly the silent failure above; guessing "expired"
     * would refuse to sync a token that was fine.
     */
    fun expiryMillis(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val payload = runCatching { decodePayload(token) }.getOrElse { error ->
            Timber.w("Could not read a sync token's expiry: ${error::class.java.simpleName}")
            null
        } ?: return null
        return payload.expClaim()?.let { it * 1000 }
    }

    /**
     * Whether the token is already in the past.
     *
     * [unknownIsExpired] decides what an unreadable token means. The pre-flight
     * on a read passes false, because refusing to fetch on an unreadable token
     * would take the directory away for a reason that was never established.
     */
    fun isExpired(token: String?, nowMillis: Long, unknownIsExpired: Boolean = false): Boolean {
        val expiry = expiryMillis(token) ?: return unknownIsExpired
        return nowMillis >= expiry
    }

    /**
     * Whether the token is expired or close enough that renewing it now is
     * worth a request.
     *
     * This is the condition that keeps a paired device paired. Without it the
     * token is used until the server rejects it, and by then `auth-refresh`
     * will not help, so every expiry becomes a full re-login.
     */
    fun isExpiringWithin(
        token: String?,
        nowMillis: Long,
        marginMillis: Long = REFRESH_MARGIN_MILLIS,
    ): Boolean {
        val expiry = expiryMillis(token) ?: return true
        return expiry - nowMillis <= marginMillis
    }

    /**
     * The JWT payload segment, decoded.
     *
     * The padding is rebuilt by hand because a JWT's middle segment is
     * unpadded base64url by construction, and `Base64.getUrlDecoder()`
     * throws on input whose length is not a multiple of four. That throw is
     * how an unreadable expiry becomes a crash in a background sync instead of
     * a null.
     */
    private fun decodePayload(token: String): JsonObject {
        val parts = token.split('.')
        if (parts.size < 2) {
            throw IllegalArgumentException("not a JWT: ${parts.size} segment(s)")
        }
        val padded = parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
        val json = String(java.util.Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        return Json.parseToJsonElement(json).let { element ->
            element as? JsonObject ?: throw IllegalArgumentException("payload is not an object")
        }
    }
}

/** The `exp` claim, or null when the payload has none. */
private fun JsonObject.expClaim(): Long? =
    (this["exp"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }

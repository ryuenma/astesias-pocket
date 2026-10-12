package com.ivy.data.sync

import com.ivy.base.legacy.SharedPrefs
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the sync server lives and who this device is.
 *
 * Unconfigured by default: a wrong URL is worse than none, because it turns a
 * clear "not set up yet" into a confusing network error.
 *
 * The password is never persisted. The server's long-lived token is the whole
 * secret, so asking for a login rather than a pasted token costs nothing and
 * keeps a long opaque string out of screenshots and shell history.
 */
@Singleton
class SyncConfig @Inject constructor(
    private val sharedPrefs: SharedPrefs,
) {
    private companion object {
        const val KEY_BASE_URL = "astesia.sync.base_url"
        const val KEY_TOKEN = "astesia.sync.token"
        const val KEY_PROFILE_ID = "astesia.sync.profile_id"
    }

    /** Null until the owner points this device at a deployed server. */
    val baseUrl: String?
        get() = sharedPrefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }

    val authToken: String?
        get() = sharedPrefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** Which of us this device is: "owner" or "so". */
    val profileId: String?
        get() = sharedPrefs.getString(KEY_PROFILE_ID, null)?.takeIf { it.isNotBlank() }

    /** Sync only runs when all three are present. */
    val isConfigured: Boolean
        get() = baseUrl != null && authToken != null && profileId != null

    /**
     * Exchange email + password for a long-lived token.
     *
     * The password is used once, over the wire, and never stored. This exists so
     * the owner types their login rather than pasting a long opaque token
     * copied out of a server log - a pasted token is easy to get wrong, easy
     * to leak into a screenshot, and impossible to rotate by hand.
     *
     * @return the outcome, so the caller can explain the failure rather than
     *   showing one catch-all message.
     */
    suspend fun signIn(baseUrl: String, email: String, password: String): DirectoryAuthApi.Result {
        val normalised = baseUrl.trimEnd('/')
        if (normalised.isBlank() || email.isBlank() || password.isBlank()) {
            return DirectoryAuthApi.Result(DirectoryAuthApi.Outcome.BAD_CREDENTIALS)
        }
        return try {
            val result = DirectoryAuthApi.token(normalised, email, password)
            result.credentials?.let { creds ->
                sharedPrefs.putString(KEY_BASE_URL, normalised)
                sharedPrefs.putString(KEY_TOKEN, creds.token)
                sharedPrefs.putString(KEY_PROFILE_ID, creds.profileId)
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Sync sign-in failed")
            // Previously this returned a bare UNREACHABLE, which is how the modal
            // ended up saying "could not reach the server" with nothing to act on.
            // DirectoryAuthApi handles network failures internally and attaches a
            // cause, so getting here at all means something threw OUTSIDE that
            // block — and that something deserves naming rather than swallowing.
            DirectoryAuthApi.Result(
                DirectoryAuthApi.Outcome.UNREACHABLE,
                detail = "${e::class.java.simpleName}: ${e.message ?: "sign-in failed before the server answered"}",
            )
        }
    }

    /**
     * Whether the stored token is still accepted, read from its own `exp`.
     *
     * Deliberately NOT a network call. An expired token is not a refusal the
     * server has to make for us to know about: measured live 2026-10-13, a
     * write with one is refused `400 {"data":{},"message":"Failed to create
     * record."}` while a READ returns **HTTP 200 with `totalItems: 0`**. That
     * second one is the dangerous signature - the directory refresh looks like
     * it succeeded, the picker keeps serving stale rows, and nothing anywhere
     * reports a problem. Reading the expiry locally is what makes the failure
     * detectable before a request is spent on it.
     *
     * Returns true when the token cannot be read at all, so an unparseable
     * token is treated as usable and the server gets to refuse it. Guessing
     * "expired" there would take sync away for a reason nobody established.
     */
    val isTokenUsable: Boolean
        get() = !SyncToken.isExpired(authToken, System.currentTimeMillis())

    /**
     * When the stored token stops being accepted, as epoch millis, or null.
     *
     * Exposed so the UI can say "expired" rather than "Connected", which is the
     * row's current claim and is simply false while the token is dead.
     */
    val tokenExpiryMillis: Long?
        get() = SyncToken.expiryMillis(authToken)

    /**
     * Renew the token if it is close to expiry.
     *
     * Called BEFORE every sync request rather than after a failure, and that
     * ordering is the whole design: `auth-refresh` refuses an ALREADY-EXPIRED
     * token with 401 (measured live), so there is no repairing a dead pairing,
     * only renewing a live one. Tokens last 120 hours and this renews with a
     * day of slack left, so a device used at all stays paired indefinitely.
     *
     * @return what happened. A caller that sees anything other than
     *   [Renewal.Renewed] carries on with the token it has, because the request
     *   may well still succeed - only the SERVER knows whether a token is good,
     *   and this is an optimisation, not a gate.
     */
    suspend fun renewTokenIfNeeded(): Renewal {
        val base = baseUrl ?: return Renewal.NotConfigured
        val current = authToken ?: return Renewal.NotConfigured
        if (!SyncToken.isExpiringWithin(current, System.currentTimeMillis())) {
            return Renewal.NotNeeded
        }
        val result = DirectoryAuthApi.refresh(base, current)
        val creds = result.credentials ?: return when (result.outcome) {
            // The renewal came too late. The token is dead and only the password
            // can fix it, so say exactly that instead of leaving the row
            // claiming a connection that cannot carry a request.
            DirectoryAuthApi.Outcome.BAD_CREDENTIALS -> Renewal.Expired(
                result.detail ?: "This connection expired. Sign in again."
            )
            // Offline, timeout, DNS, TLS. The old token may still be perfectly
            // good for another day, so nothing is invalidated and the request
            // proceeds on it.
            else -> Renewal.Unreachable(result.detail)
        }
        // Guard against a server that hands back a token for somebody else's
        // profile. Writing it would silently re-point this device at the wrong
        // identity, and every subsequent request would carry a correlationId
        // this device did not issue.
        if (creds.profileId.isNotBlank() && creds.profileId != profileId) {
            Timber.e("Token refresh changed profile $profileId -> ${creds.profileId}; refusing")
            return Renewal.Expired("The server returned a different profile. Sign in again.")
        }
        sharedPrefs.putString(KEY_TOKEN, creds.token)
        Timber.i("Sync token renewed, valid for another ${SyncToken.TOKEN_LIFETIME_MILLIS / 3_600_000}h")
        return Renewal.Renewed(creds.token)
    }

    /**
     * What a renewal attempt achieved.
     *
     * Four cases rather than a boolean because they need four different
     * reactions, and collapsing them is how this bug stayed invisible: a
     * network blip and a genuinely dead pairing both used to read as "HTTP 400"
     * on a queue row.
     */
    sealed interface Renewal {
        /** Nothing to renew, or nowhere to renew it. */
        data object NotConfigured : Renewal

        /** The token had plenty of life left; no request was made. */
        data object NotNeeded : Renewal

        /** Renewed; [token] is the new one, already persisted. */
        data class Renewed(val token: String) : Renewal

        /**
         * The token was already past expiry and `auth-refresh` refused it.
         * [reason] is meant for a person. This is the ONE case that requires
         * the owner, and it must never be mistaken for a network problem.
         */
        data class Expired(val reason: String) : Renewal

        /**
         * The renewal could not be attempted. The stored token is untouched and
         * may well still work.
         */
        data class Unreachable(val reason: String?) : Renewal
    }

    fun configure(baseUrl: String, token: String, profileId: String) {
        // Normalise so callers may or may not include a trailing slash.
        val normalised = baseUrl.trimEnd('/')
        require(normalised.isNotBlank()) { "Sync base URL cannot be blank" }
        require(profileId.isNotBlank()) { "Profile id cannot be blank" }
        sharedPrefs.putString(KEY_BASE_URL, normalised)
        sharedPrefs.putString(KEY_TOKEN, token)
        sharedPrefs.putString(KEY_PROFILE_ID, profileId)
    }

    /**
     * Forget the server. Called on sign-out.
     *
     * The local directory cache is NOT cleared here: it is a display cache of
     * "accounts that exist", not credentials, and a stale name is harmless
     * where a stale token would not be.
     */
    fun clear() {
        sharedPrefs.remove(KEY_BASE_URL)
        sharedPrefs.remove(KEY_TOKEN)
        sharedPrefs.remove(KEY_PROFILE_ID)
    }
}
package com.ivy.data.sync

import com.ivy.base.legacy.SharedPrefs
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the sync server lives and who this device is.
 *
 * Deliberately unconfigured by default. A wrong hardcoded URL is worse than no
 * URL: it turns a clear "sync is not set up yet" into a confusing network
 * error, and it risks pointing the owner's real ledger at a host he did not
 * intend.
 *
 * Credentials are stored as a token only. The password is never persisted -
 * the server issues a long-lived auth token and that is the whole secret.
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
            DirectoryAuthApi.Result(DirectoryAuthApi.Outcome.UNREACHABLE)
        }
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
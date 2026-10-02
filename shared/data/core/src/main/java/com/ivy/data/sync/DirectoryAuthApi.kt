package com.ivy.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import timber.log.Timber

/**
 * One-shot credential exchange against the sync server.
 *
 * Separate from [DirectorySyncApi] on purpose: signing in is the only request
 * made before a token exists, and that request must not try to read a token
 * from config that is not configured yet.
 */
object DirectoryAuthApi {

    @Serializable
    private data class AuthResponse(
        val token: String = "",
        val record: ProfileRecord = ProfileRecord(),
    )

    @Serializable
    private data class ProfileRecord(
        val profileId: String = "",
        val displayName: String = "",
    )

    data class Credentials(val token: String, val profileId: String, val displayName: String)

    /**
     * @return credentials on success, null when the server refuses. A wrong
     *   password and an unreachable host both return null; the caller shows one
     *   message rather than pretending to know which happened.
     */
    suspend fun token(baseUrl: String, email: String, password: String): Credentials? {
        val client = HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            expectSuccess = false
        }
        return try {
            val response = client.post("$baseUrl/api/collections/profiles/auth-with-password") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"identity":"${email.jsonEscaped()}","password":"${password.jsonEscaped()}"}"""
                )
            }
            if (response.status != HttpStatusCode.OK) {
                Timber.w("Sync sign-in refused: HTTP ${response.status}")
                return null
            }
            val auth: AuthResponse = response.body()
            if (auth.token.isBlank() || auth.record.profileId.isBlank()) {
                Timber.w("Sync sign-in returned an incomplete profile")
                null
            } else {
                Credentials(auth.token, auth.record.profileId, auth.record.displayName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Sync sign-in threw")
            null
        } finally {
            client.close()
        }
    }
}

/** Minimal JSON string escape, so a quote or backslash in a password cannot corrupt the body. */
private fun String.jsonEscaped(): String = buildString {
    for (c in this@jsonEscaped) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
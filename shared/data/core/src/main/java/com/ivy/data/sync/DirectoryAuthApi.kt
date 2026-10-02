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
     * WHY this is typed rather than returning null on every failure:
     *
     * "Could not connect" was actively misleading. It blamed the network when the
     * real cause was "that account does not exist", and it cost several rounds of
     * guessing before anyone checked whether the profiles had been seeded at all.
     * The owner-facing message must name the actual cause.
     */
    enum class Outcome {
        /** Signed in; [Credentials] is populated. */
        SUCCESS,

        /** The host could not be reached at all - DNS, TLS, timeout, no network. */
        UNREACHABLE,

        /** The server answered, and refused these credentials. */
        BAD_CREDENTIALS,

        /** The server answered, but not with something we understand. */
        SERVER_ERROR,
    }

    data class Result(val outcome: Outcome, val credentials: Credentials? = null, val httpStatus: Int = 0)

    /** @return the outcome, never null, so the caller can explain rather than shrug. */
    suspend fun token(baseUrl: String, email: String, password: String): Result {
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
                return Result(
                    // 400 from PocketBase means the identity/password pair did not
                    // match. That is the signature of "profile was never seeded",
                    // which is the single most likely cause here.
                    outcome = if (response.status == HttpStatusCode.BadRequest) {
                        Outcome.BAD_CREDENTIALS
                    } else {
                        Outcome.SERVER_ERROR
                    },
                    httpStatus = response.status.value,
                )
            }
            val auth: AuthResponse = response.body()
            if (auth.token.isBlank() || auth.record.profileId.isBlank()) {
                Timber.w("Sync sign-in returned an incomplete profile")
                Result(Outcome.SERVER_ERROR, httpStatus = response.status.value)
            } else {
                Result(
                    Outcome.SUCCESS,
                    Credentials(auth.token, auth.record.profileId, auth.record.displayName)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Sync sign-in threw")
            Result(Outcome.UNREACHABLE)
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
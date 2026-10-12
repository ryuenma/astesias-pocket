package com.ivy.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

    data class Result(
        val outcome: Outcome,
        val credentials: Credentials? = null,
        val httpStatus: Int = 0,
        /**
         * WHY it failed, in words meant for a person, or null when there is nothing
         * useful to add beyond [outcome].
         *
         * This exists because the four [Outcome] values alone were not enough in the
         * field. "UNREACHABLE" covers a wrong hostname, a wrong path, a TLS handshake
         * the phone refused, and a plain timeout — four problems with four different
         * fixes, and the owner could only ever see one of them. Timber is planted only
         * in debug builds and was not reaching logcat on the emulator at all, so a log
         * line is not a diagnostic either.
         *
         * So the cause is carried up to the screen and shown. It is derived from the
         * exception type and never echoes the URL's credentials or the password; the
         * hostname is included because "which server did it even try?" is the first
         * question every time.
         */
        val detail: String? = null,
    )

    /** @return the outcome, never null, so the caller can explain rather than shrug. */
    suspend fun token(baseUrl: String, email: String, password: String): Result {
        // Built INSIDE the try on purpose. Constructing an OkHttp engine can itself
        // throw (a missing provider, a TLS setup failure), and that used to escape
        // uncaught from this function, be swallowed further up as a bare UNREACHABLE,
        // and leave the owner with no idea why. Anything this function does must be
        // either a Result or an explained throw.
        var client: HttpClient? = null
        return try {
            client = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 20_000
                }
            install(ContentNegotiation) {
                // PocketBase returns an ENTIRE auth record inside `record` -
                // collectionId, email, verified and a dozen more - and
                // kotlinx.serialization refuses unknown keys by default. Being
                // strict about a server's own envelope is how a perfectly good
                // 200 becomes a JsonConvertException.
                json(Json { ignoreUnknownKeys = true })
            }
                expectSuccess = false
            }
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
            Result(Outcome.UNREACHABLE, detail = describe(e, baseUrl))
        } finally {
            client?.close()
        }
    }

    /**
     * Turns a still-live token into a fresh one.
     *
     * WHY this exists and why it is not a fix on its own:
     *
     * Measured against the live server 2026-10-13, an ALREADY-EXPIRED token
     * gets **HTTP 401 `"The request requires valid record authorization
     * token."`** - so refreshing is not a repair, it is a renewal, and it only
     * works while the old token still counts. The callers therefore use
     * [SyncToken.isExpiringWithin] to renew EARLY rather than calling this
     * after a refusal. A pairing that is never refreshed is a pairing that
     * eventually needs the password again, and this cannot change that.
     *
     * The endpoint is a POST with an empty body and the token in
     * `Authorization`; there is no request payload. A GET returns 404
     * `"File not found."`, which is a trap worth naming because it reads like
     * the route does not exist when in fact it does and only the verb is wrong.
     *
     * @return the outcome, never null. [Outcome.SUCCESS] carries the new
     *   token in [Credentials]; note that the record comes back complete, so
     *   the caller can confirm the profileId is unchanged rather than assuming.
     */
    suspend fun refresh(baseUrl: String, token: String): Result {
        if (token.isBlank()) {
            return Result(Outcome.BAD_CREDENTIALS, detail = "There is no token to refresh.")
        }
        var client: HttpClient? = null
        return try {
            client = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 20_000
                }
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                expectSuccess = false
            }
            val response = client.post("$baseUrl/api/collections/profiles/auth-refresh") {
                header("Authorization", token)
                contentType(ContentType.Application.Json)
                // An empty body. PocketBase's auth-refresh reads the caller's
                // identity from the token and takes no fields at all.
                setBody("{}")
            }
            if (response.status != HttpStatusCode.OK) {
                Timber.w("Sync token refresh refused: HTTP ${response.status}")
                return Result(
                    // 401 here means the token was already past its exp - the
                    // renewal came too late. That is the state the owner has to
                    // act on, and it must not be dressed up as a network problem.
                    outcome = when (response.status) {
                        HttpStatusCode.Unauthorized, HttpStatusCode.BadRequest ->
                            Outcome.BAD_CREDENTIALS
                        else -> Outcome.SERVER_ERROR
                    },
                    httpStatus = response.status.value,
                    detail = when (response.status) {
                        HttpStatusCode.Unauthorized ->
                            "This connection has expired and can no longer be renewed " +
                                "automatically. Sign in again."
                        else -> null
                    },
                )
            }
            val auth: AuthResponse = response.body()
            if (auth.token.isBlank() || auth.record.profileId.isBlank()) {
                Timber.w("Sync token refresh returned an incomplete profile")
                Result(Outcome.SERVER_ERROR, httpStatus = response.status.value)
            } else {
                Result(
                    Outcome.SUCCESS,
                    Credentials(auth.token, auth.record.profileId, auth.record.displayName),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Sync token refresh threw")
            Result(Outcome.UNREACHABLE, detail = describe(e, baseUrl))
        } finally {
            client?.close()
        }
    }

    /**
     * Turn a network exception into a sentence the owner can act on.
     *
     * The point is that "UNREACHABLE" is not one problem. A misspelt host, a phone
     * with no route, and a certificate the platform will not accept all arrive as
     * the same outcome, and the fix for each is different: correct the address, turn
     * on data, or wait for a valid certificate. Naming which one happened is the
     * whole value here.
     *
     * Reads the CAUSE chain rather than only the outermost exception, because OkHttp
     * and Ktor routinely wrap the interesting failure — an UnknownHostException
     * three layers down is the real answer and the wrapper says nothing.
     *
     * Deliberately echoes only the HOSTNAME. The path and the password never appear.
     */
    private fun describe(e: Exception, baseUrl: String): String {
        val host = baseUrl.substringAfter("://").substringBefore('/').ifBlank { baseUrl }
        var cause: Throwable? = e
        val seen = mutableSetOf<Throwable>()
        while (cause != null && seen.add(cause)) {
            val text = "${cause::class.java.simpleName}: ${cause.message ?: "no message"}"
            return when {
                text.contains("Unable to resolve host", true) ||
                    cause is java.net.UnknownHostException ->
                    "Could not find $host. Check the address for typos, and that this device has a connection."
                text.contains("CLEARTEXT", true) ->
                    "The server answered over plain HTTP but the app requires HTTPS. Change the address to https://$host"
                text.contains("SSL", true) || text.contains("certificate", true) ||
                    text.contains("TrustAnchor", true) || text.contains("handshake", true) ->
                    "The secure connection to $host was refused. The certificate may be expired or not trusted by this device."
                text.contains("timeout", true) || text.contains("timed out", true) ->
                    "$host did not answer in time. It may be overloaded, or the network may be dropping the request."
                text.contains("ECONNREFUSED", true) ||
                    text.contains("Failed to connect", true) ||
                    text.contains("ConnectException", true) ->
                    "Nothing is listening on $host. The address may point somewhere that is not running."
                text.contains("EHOSTUNREACH", true) || text.contains("ENETUNREACH", true) ->
                    "This device has no route to $host. Check that mobile data or Wi-Fi is actually on."
                else -> {
                    cause = cause.cause
                    continue
                }
            }
        }
        return "Could not reach $host. ${e::class.java.simpleName}: ${e.message ?: "no further detail"}"
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
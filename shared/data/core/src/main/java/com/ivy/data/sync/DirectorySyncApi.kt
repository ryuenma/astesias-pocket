package com.ivy.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

/**
 * Talks to the PocketBase sync server.
 *
 * Only the two read paths the transfer picker depends on are implemented here.
 * Writing a paired transfer is deliberately NOT here yet: it has to land
 * atomically with the local leg, and doing that from a network client is how
 * you end up with money moved on one phone and never on the other.
 *
 * Every method returns null on failure rather than throwing. Sync is a
 * background nicety - a transfer the owner already made must still be
 * recordable when the server is down.
 */
@Singleton
class DirectorySyncApi @Inject constructor(
    private val config: SyncConfig,
) {
    @Serializable
    data class PbList<T>(
        val items: List<T> = emptyList(),
        @SerialName("totalItems") val totalItems: Int = 0,
    )

    /** A profile as stored on the server. Maps profileId -> display name. */
    @Serializable
    data class RemoteProfile(
        @SerialName("profileId") val profileId: String = "",
        @SerialName("displayName") val displayName: String = "",
    )

    /**
     * One published account.
     *
     * [localAccountId] is the OWNER's local Room account id, carried as a
     * string because it crosses the wire as text. It is parsed back into a
     * UUID by the caller, and a row whose id will not parse is dropped rather
     * than guessed at.
     */
    @Serializable
    data class RemoteDirectoryAccount(
        @SerialName("localAccountId") val localAccountId: String = "",
        @SerialName("profileId") val profileId: String = "",
        @SerialName("accountName") val accountName: String = "",
        @SerialName("currency") val currency: String = "",
    )

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            // Short and finite: a phone on mobile data should fail fast and
            // retry later rather than hold the caller open.
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    /** Every profile known to the server, used to resolve id -> display name. */
    suspend fun fetchProfiles(): PbList<RemoteProfile>? = get("/api/collections/profiles/records")

    /** Every published account from every profile. */
    suspend fun fetchDirectory(): PbList<RemoteDirectoryAccount>? =
        get("/api/collections/directory_accounts/records")

    private suspend inline fun <reified T> get(path: String): T? {
        val base = config.baseUrl ?: run {
            Timber.i("Sync not configured; skipping $path")
            return null
        }
        val token = config.authToken ?: run {
            Timber.i("No sync token; skipping $path")
            return null
        }
        return try {
            val response = client.get(base + path) {
                header("Authorization", token)
                contentType(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.OK) {
                response.body()
            } else {
                Timber.w("Sync GET $path failed: HTTP ${response.status}")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Sync GET $path threw")
            null
        }
    }
}
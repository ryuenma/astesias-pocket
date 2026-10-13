package com.ivy.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Talks to the PocketBase sync server: both directions of the account directory.
 *
 * A re-publish of an unchanged account is refused by the server's unique index,
 * and that refusal is a SUCCESS - hence a result type rather than `Unit?`. Reads
 * return null on failure rather than throwing. Full reasoning:
 * docs/decisions/sync-design.md
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
     *
     * [recordId] is PocketBase's own per-record id, and it is NOT
     * [localAccountId]. It is the only handle that can address the row for an
     * update, and it is a server-generated opaque string, which is exactly why
     * it never reaches the local database: the read path keys everything on the
     * owner's local id, and a server id that changed under a restore would
     * silently point at the wrong row.
     */
    @Serializable
    data class RemoteDirectoryAccount(
        @SerialName("id") val recordId: String = "",
        @SerialName("localAccountId") val localAccountId: String = "",
        @SerialName("profileId") val profileId: String = "",
        @SerialName("accountName") val accountName: String = "",
        @SerialName("currency") val currency: String = "",
    )

    /**
     * What one publish attempt achieved.
     *
     * Three cases, and the second one is the load-bearing one: see the class
     * doc. [AlreadyPublished] is a success because the unique index refusing a
     * create proves exactly one correct row exists on the server.
     */
    sealed interface PublishResult {
        /** The server took the row. */
        data object Accepted : PublishResult

        /**
         * The server already holds this (profileId, localAccountId).
         *
         * A SUCCESS. It means the index refused the create precisely because
         * one correct record exists, which is the state the publish wanted.
         * Reporting it as a failure would retry a create the server will refuse
         * identically on every attempt.
         */
        data object AlreadyPublished : PublishResult

        /**
         * We do not know: unreachable, timed out, refused, or a 400 that is not
         * the duplicate. Nothing is claimed to have been published.
         */
        data class Undelivered(val reason: String) : PublishResult
    }

    /**
     * The create body.
     *
     * [profileId] is THIS device's own id from [SyncConfig] and is never a
     * parameter: the server's `createRule` is
     * `@request.auth.profileId = @request.body.profileId`, so a body carrying
     * anyone else's id is rejected - and if the rule were ever loosened, a
     * client that could name another profile would let a compromised token
     * inject a fake account belonging to the other person into their real
     * transfer picker. Reading it from config is what makes that unrepresentable
     * rather than merely discouraged.
     */
    @Serializable
    data class DirectoryAccountRequest(
        @SerialName("localAccountId") val localAccountId: String,
        @SerialName("accountName") val accountName: String,
        @SerialName("currency") val currency: String,
        @SerialName("profileId") val profileId: String,
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
    }

    /** Every profile known to the server, used to resolve id -> display name. */
    suspend fun fetchProfiles(): PbList<RemoteProfile>? = get("/api/collections/profiles/records")

    /**
     * Every published account from every profile, ACROSS EVERY PAGE.
     *
     * ## Why one request is not enough, measured
     *
     * PocketBase pages every list and its default page size is 30, so the bare
     * `/api/collections/directory_accounts/records` returns at most 30 rows and
     * says nothing about the rest. Measured against the LIVE server 2026-10-13:
     * the collection held 31 rows and the bare URL answered
     * `items.size = 30, totalItems = 31, perPage = 30`, with the newest row
     * (`QaBWallet`) simply absent; the same URL with `perPage=200` returned all
     * 31. The client logged `Directory refreshed: 30 account(s)` either way,
     * because it counted what it fetched rather than what existed.
     *
     * This is not a large-directory edge case. The owner's own fixture publishes
     * 21 accounts, so one more phone crosses the limit immediately - and because
     * [DirectorySyncRepository] clears each profile it saw in the response before
     * inserting what it fetched, a truncated page DELETEs the rows past the page
     * boundary from the local cache and never brings them back. The picker then
     * silently omits real accounts, with no error anywhere.
     *
     * Returns null when ANY page fails, so a partial directory is never passed
     * off as a complete one: the caller keeps the cache it already has.
     */
    // ReturnCount: two failure exits that mean the same thing ("a page did not
    // arrive, so there is no directory") and one result exit. Merging the two
    // failures would need a flag threaded through the loop, which is more code
    // to hide the same null.
    @Suppress("ReturnCount")
    suspend fun fetchDirectory(): PbList<RemoteDirectoryAccount>? {
        val first = fetchDirectoryPage(1) ?: return null
        val collected = mutableListOf<RemoteDirectoryAccount>()
        collected += first.items
        var page = 2
        // `collected.size < totalItems` is the whole stop condition, and it is
        // false on the first pass for a single-page directory - so the common
        // case still costs exactly one request. No separate short-circuit, so
        // this function has one failure exit and one result exit.
        while (collected.size < first.totalItems && page <= DIRECTORY_MAX_PAGES) {
            val next = fetchDirectoryPage(page) ?: return null
            // A server that reports more rows than it will serve must not spin
            // this loop: stop on the first empty page rather than asking again.
            if (next.items.isEmpty()) break
            collected += next.items
            page++
        }
        return first.copy(items = collected)
    }

    /**
     * One page of the directory.
     *
     * Split out from [fetchDirectory] so the paging loop is testable at all: the
     * HTTP client below is a private `lazy` OkHttp instance with no injection
     * seam, so this is the only place a unit test can stand between the loop and
     * the network.
     *
     * [DIRECTORY_PAGE_SIZE] is passed explicitly rather than left to the
     * server's default, because that default is the bug.
     */
    suspend fun fetchDirectoryPage(page: Int): PbList<RemoteDirectoryAccount>? =
        get("/api/collections/directory_accounts/records?page=$page&perPage=$DIRECTORY_PAGE_SIZE")

    // ---------------------------------------------------------------------
    // Publishing
    // ---------------------------------------------------------------------

    /**
     * Publish one of THIS device's own accounts.
     *
     * [localAccountId] is the account's own Room id as text - the same string
     * the read path parses back into a UUID. The server never sees an id it can
     * resolve on its own, and does not need to: the receiving device resolves
     * the name locally.
     *
     * Never throws. Returns [PublishResult.AlreadyPublished] rather than
     * failing when the unique index refuses a re-publish, which is what makes
     * it safe to call this on every account change and on every app start.
     */
    suspend fun publishAccount(
        localAccountId: String,
        accountName: String,
        currency: String,
    ): PublishResult {
        val profileId = config.profileId ?: run {
            Timber.i("No sync profile; not publishing $localAccountId")
            return PublishResult.Undelivered("not configured")
        }
        val body = DirectoryAccountRequest(
            localAccountId = localAccountId,
            accountName = accountName,
            // The server's `currency` field is required and rejects a blank
            // string, so a nameless/curless account is published with the
            // caller's resolved fallback rather than rejected outright.
            currency = currency.ifBlank { FALLBACK_CURRENCY },
            profileId = profileId,
        )
        return post("/api/collections/directory_accounts/records", body, "directory $localAccountId")
    }

    /**
     * Rename/re-currency a row this device already published.
     *
     * PocketBase addresses records by their server-side id, which is NOT the
     * local account id - so the caller has to have learned it from a previous
     * create or from [fetchDirectory], which returns it as
     * [RemoteDirectoryAccount.recordId].
     * Republishing instead would hit the unique index and change nothing, so a
     * rename has to go through here or not at all.
     *
     * The server's `updateRule` is `@request.auth.profileId = profileId`, so
     * this may only ever touch this device's own rows.
     */
    suspend fun updateAccount(
        recordId: String,
        accountName: String,
        currency: String,
    ): PublishResult {
        if (recordId.isBlank()) {
            // No server id means we cannot address the row, and posting instead
            // would be refused by the unique index. Claiming success would make
            // a rename that never happened look done.
            Timber.w("No directory record id; cannot update account")
            return PublishResult.Undelivered("no record id")
        }
        val body = DirectoryAccountRequest(
            localAccountId = "",
            accountName = accountName,
            currency = currency.ifBlank { FALLBACK_CURRENCY },
            // Present but irrelevant to an update: the row's profileId is what
            // the rule checks, and the server rejects a body that tries to move
            // a row between profiles.
            profileId = config.profileId.orEmpty(),
        )
        return patch(
            "/api/collections/directory_accounts/records/$recordId",
            body,
            "directory record $recordId",
        )
    }

    private suspend inline fun <reified T> get(path: String): T? {
        val base = config.baseUrl ?: run {
            Timber.i("Sync not configured; skipping $path")
            return null
        }
        if (!ensureUsableToken(path)) {
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

    /**
     * Renew the token if it needs it, and say whether the request may proceed.
     *
     * ## Why the read path needs this more than the write path does
     *
     * Measured live 2026-10-13: an expired token gets a WRITE refused
     * `400 {"data":{},"message":"Failed to create record."}`, which is loud and
     * lands in the outbox row's `lastError`. A READ gets **HTTP 200 with
     * `totalItems: 0`** - PocketBase answers an anonymous list with an empty
     * page rather than a 403. So the directory refresh appears to succeed,
     * reports zero accounts, and [DirectorySyncRepository] deliberately clears
     * only the profiles present in the response - which means the picker keeps
     * serving stale rows and NOTHING anywhere says a request failed.
     *
     * That is the dangerous half of this bug. The local `exp` check is what
     * closes it: a token already in the past never gets to make the request, so
     * the caller sees a null and leaves the cache alone rather than being told
     * "zero accounts, all good".
     *
     * @return false when the token is known dead and the request would be made
     *   anonymously. True otherwise - including when the token merely could not
     *   be renewed because the phone is offline, because that token may still
     *   have another day of life and only the server can refuse it.
     */
    private suspend fun ensureUsableToken(what: String): Boolean {
        val renewal = config.renewTokenIfNeeded()
        if (renewal is SyncConfig.Renewal.Expired) {
            Timber.w("Sync token expired; $what not attempted. ${renewal.reason}")
            return false
        }
        // A renewal that failed for a network reason leaves the old token in
        // place, so the local check is re-read rather than assumed.
        if (!config.isTokenUsable) {
            Timber.w("Sync token is past its expiry; $what not attempted")
            return false
        }
        return true
    }

    /**
     * One write path for POST and PATCH, because they fail identically and the
     * difference is the verb alone.
     *
     * Reads the failure body as TEXT rather than decoding it: a 400 is not the
     * shape we asked for, and decoding it to find out why would throw inside
     * the error path.
     */
    private suspend fun write(
        method: HttpMethod,
        path: String,
        body: DirectoryAccountRequest,
        what: String,
    ): PublishResult {
        val base = config.baseUrl ?: run {
            Timber.i("Sync not configured; $what not published")
            return PublishResult.Undelivered("not configured")
        }
        if (!ensureUsableToken(what)) {
            // Named rather than "HTTP 400": the row records this string as its
            // lastError, and "HTTP 400 " is exactly the unexplained dead end
            // that took a device dump and a JWT decode to diagnose.
            return PublishResult.Undelivered("sign-in expired")
        }
        val token = config.authToken ?: run {
            Timber.i("No sync token; $what not published")
            return PublishResult.Undelivered("no token")
        }
        return try {
            val response = client.request(base + path) {
                this.method = method
                header("Authorization", token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val text = runCatching { response.body<String>() }.getOrNull()
            val result = classify(response.status, text)
            if (result is PublishResult.Undelivered) {
                Timber.w("Sync $method $path failed: ${result.reason}")
            } else {
                Timber.i("Sync $method $path: $result")
            }
            result
        } catch (e: Exception) {
            // Never propagates. The account is already saved locally and the
            // next publish will try again; an exception here would take down
            // whatever coroutine happened to be saving an account.
            Timber.e(e, "Sync $method $path threw")
            PublishResult.Undelivered(e.javaClass.simpleName)
        }
    }

    private suspend fun post(
        path: String,
        body: DirectoryAccountRequest,
        what: String,
    ): PublishResult = write(HttpMethod.Post, path, body, what)

    private suspend fun patch(
        path: String,
        body: DirectoryAccountRequest,
        what: String,
    ): PublishResult = write(HttpMethod.Patch, path, body, what)

    companion object {
        /**
         * What a published account carries when its own currency is blank.
         *
         * The server marks `currency` required, and PocketBase rejects a blank
         * string on a required text field as readily as a missing one - so
         * omitting it fails the create for a reason that has nothing to do with
         * the account. "IDR" is the fallback because this app is Indonesian and
         * that is the overwhelmingly common real case; the alternative,
         * dropping the account from the directory entirely, hides a real
         * account from the other phone's picker.
         */
        const val FALLBACK_CURRENCY = "IDR"

        /**
         * How many directory rows one request asks for.
         *
         * Stated explicitly, because PocketBase's default is 30 and that default
         * is what silently truncated the directory: rows past the first page
         * never reached a device, and the refresh DELETES each profile's rows
         * before re-inserting them, so the ones past the boundary were removed
         * from the local cache as well. 200 is comfortably under PocketBase's
         * 500 ceiling and makes a two-person directory a single request.
         */
        const val DIRECTORY_PAGE_SIZE = 200

        /**
         * A ceiling on pages, so a server that advertises a `totalItems` it will
         * never actually serve cannot spin the loop forever. 50 x 200 = 10,000
         * accounts, which is far past anything this app should hold.
         */
        const val DIRECTORY_MAX_PAGES = 50

        /**
         * The one place a directory publish outcome is decided, so the rule is
         * stated once and is testable exhaustively without standing up a server.
         *
         * Pure policy over a status code and a body, which is the part that has
         * to be right for responses no server returns on a normal day.
         */
        fun classify(status: HttpStatusCode, body: String?): PublishResult = when {
            status == HttpStatusCode.OK || status == HttpStatusCode.Created ->
                PublishResult.Accepted

            // 400 from PocketBase on a create is a failed validation, and the
            // ONLY unique constraint on this collection is
            // idx_directory_owner_local (profileId, localAccountId). So this is
            // the duplicate re-publish - and it is a success, because it proves
            // one correct row exists. Only honoured when the body actually says
            // so; see `mentionsDuplicate`.
            status == HttpStatusCode.BadRequest && body.mentionsDuplicate() ->
                PublishResult.AlreadyPublished

            status == HttpStatusCode.Conflict -> PublishResult.AlreadyPublished

            else -> PublishResult.Undelivered("HTTP $status")
        }
    }
}

/**
 * Whether a failure body is the unique-constraint rejection.
 *
 * ## The code is `validation_not_unique`, and the difference matters
 *
 * Measured against the LIVE server 2026-10-13, a duplicate create on
 * directory_accounts returns HTTP 400 with
 * `{"data":{"localAccountId":{"code":"validation_not_unique","message":"Value
 * must be unique."},"profileId":{"code":"validation_not_unique",...}}}`.
 * `validation_invalid_unique` — the string this used to look for — is what
 * PocketBase's *field-level* `unique: true` produces, and this project has no
 * field-level unique: uniqueness is real SQL in the `indexes` array, because
 * `unique: true` is silently ignored on 0.40. So the only unique rejection
 * this server can ever emit is `validation_not_unique`, and looking for the
 * other string meant the check never fired.
 *
 * The consequence was the exact failure this whole class exists to prevent: a
 * re-publish of an account the server already holds was classified
 * Undelivered("HTTP 400") instead of AlreadyPublished, so the account counted
 * as undelivered on every sync, forever, while its row sat correct on the
 * server. Both strings are accepted now because a PocketBase upgrade or a
 * future field-level unique would produce the other one, and being strict
 * about a code the server does not currently emit buys nothing.
 *
 * The check stays loose on the surrounding JSON and strict on the field: a 400
 * that does NOT name an indexed column is a genuine validation failure, and
 * calling that "already published" would report an account as visible on the
 * other phone when the server never stored it.
 *
 * The field names are the index's own columns —
 * idx_directory_owner_local is (profileId, localAccountId) — so a body naming
 * some other collection's field is not this index refusing.
 */
private fun String?.mentionsDuplicate(): Boolean =
    this != null &&
        (
            contains("validation_not_unique", ignoreCase = true) ||
                contains("validation_invalid_unique", ignoreCase = true)
            ) &&
        (
            contains("profileId", ignoreCase = true) ||
                contains("localAccountId", ignoreCase = true)
            )
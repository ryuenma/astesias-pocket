package com.ivy.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wire: publishes transfers and confirmations, reads back the counterpart's.
 *
 * A publish must distinguish "accepted" / "already had this" / "we do not know" -
 * the first two clear the queue row, the third must leave it queued. Never
 * throws. Full reasoning: docs/decisions/sync-design.md
 */
@Singleton
class TransferSyncApi @Inject constructor(
    private val config: SyncConfig,
) {

    @Serializable
    // Defaults are the point: a server that adds a field must not stop
    // this client decoding a list envelope, so every field is optional to the
    // decoder and the validation lives in `TransferEventSync`, which can
    // refuse a row deliberately rather than crashing on a missing key.
    @Suppress("DataClassDefaultValues")
    data class PbList<T>(
        val items: List<T> = emptyList(),
        @SerialName("totalItems") val totalItems: Int = 0,
    )

    /**
     * A transfer event as stored on the server.
     *
     * Field names and types mirror `1700000000_collections.js` exactly. Note
     * what is NOT here: no account ids on either side. The two legs live in two
     * databases and share no account id, so the wire carries names, and
     * `correlationId` is the only thing the two records agree on.
     */
    @Serializable
    // Defaults are the point: a server that adds a field must not stop
    // this client decoding a transfer event, so every field is optional to the
    // decoder and the validation lives in `TransferEventSync`, which can
    // refuse a row deliberately rather than crashing on a missing key.
    @Suppress("DataClassDefaultValues")
    data class RemoteTransferEvent(
        @SerialName("correlationId") val correlationId: String = "",
        @SerialName("recordedBy") val recordedBy: String = "",
        @SerialName("amount") val amount: Double = 0.0,
        @SerialName("currency") val currency: String = "",
        @SerialName("occurredOn") val occurredOn: String = "",
        @SerialName("fromAccountName") val fromAccountName: String? = null,
        @SerialName("toAccountName") val toAccountName: String? = null,
    )

    /** An answer to a transfer, as stored on the server. */
    @Serializable
    // Defaults are the point: a server that adds a field must not stop
    // this client decoding a confirmation, so every field is optional to the
    // decoder and the validation lives in `TransferEventSync`, which can
    // refuse a row deliberately rather than crashing on a missing key.
    @Suppress("DataClassDefaultValues")
    data class RemoteConfirmation(
        @SerialName("correlationId") val correlationId: String = "",
        @SerialName("decidedBy") val decidedBy: String = "",
        @SerialName("decision") val decision: String = "",
    )

    /**
     * What one publish attempt achieved.
     *
     * Three cases because two is not enough, and the third is the whole point.
     */
    sealed interface PublishResult {
        /** The server accepted it. Clear the queue row. */
        data object Accepted : PublishResult

        /**
         * The server already holds this correlation id.
         *
         * A SUCCESS, not a failure - see the class doc. Clear the queue row.
         */
        data object AlreadyPublished : PublishResult

        /**
         * We do not know: unreachable, timed out, or the server said something
         * we do not understand.
         *
         * Leave the queue row alone. It will be retried, and because the key is
         * idempotent on both ends, a retry after a request that actually landed
         * produces [AlreadyPublished] rather than a second record.
         */
        data class Undelivered(val reason: String) : PublishResult
    }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
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

    // ---------------------------------------------------------------------
    // Publishing
    // ---------------------------------------------------------------------

    /**
     * Publishes the owner's leg.
     *
     * The body is built by hand rather than by serialising a DTO so the exact
     * wire shape is visible in one place next to the field list it has to match.
     * Every interpolated string goes through [escape], because a transfer
     * description is free text and an unescaped quote in it would silently
     * produce a malformed body that the server rejects for a reason that looks
     * like corruption.
     */
    suspend fun publishEvent(
        correlationId: String,
        recordedBy: String,
        amount: Double,
        currency: String,
        occurredOn: String,
        fromAccountName: String?,
        toAccountName: String?,
    ): PublishResult {
        val body = buildString {
            append("""{"correlationId":"${correlationId.escape()}"""")
            append(""","recordedBy":"${recordedBy.escape()}"""")
            append(""","amount":$amount""")
            append(""","currency":"${currency.escape()}"""")
            append(""","occurredOn":"${occurredOn.escape()}"""")
            appendNullable("fromAccountName", fromAccountName)
            appendNullable("toAccountName", toAccountName)
            append("}")
        }
        return post("/api/collections/transfer_events/records", body, "event $correlationId")
    }

    /** Publishes her answer. */
    suspend fun publishConfirmation(
        correlationId: String,
        decidedBy: String,
        decision: String,
    ): PublishResult {
        val body = buildString {
            append("""{"correlationId":"${correlationId.escape()}"""")
            append(""","decidedBy":"${decidedBy.escape()}"""")
            append(""","decision":"${decision.escape()}"""")
            append("}")
        }
        return post(
            path = "/api/collections/transfer_confirmations/records",
            body = body,
            what = "confirmation $correlationId",
        )
    }

    // ---------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------

    /**
     * Every transfer event the server knows.
     *
     * Null on failure, exactly like [DirectorySyncApi.fetchDirectory]: a failed
     * read leaves the inbox as it was, which is the correct behaviour for a
     * mirror - a stale inbox showing three items is honest, an empty one
     * claiming she has nothing pending is a lie.
     */
    suspend fun fetchEvents(): PbList<RemoteTransferEvent>? =
        get("/api/collections/transfer_events/records")

    /** Every confirmation the server knows. */
    suspend fun fetchConfirmations(): PbList<RemoteConfirmation>? =
        get("/api/collections/transfer_confirmations/records")

    // ---------------------------------------------------------------------
    // Plumbing
    // ---------------------------------------------------------------------

    private suspend inline fun post(path: String, body: String, what: String): PublishResult {
        val base = config.baseUrl ?: run {
            Timber.i("Sync not configured; $what stays queued")
            return PublishResult.Undelivered("not configured")
        }
        if (!ensureUsableToken(what)) {
            // Named rather than left as "HTTP 400 ": this string is the row's
            // `lastError`, and an unexplained "HTTP 400 " is exactly the dead
            // end that took a device dump and a hand-decoded JWT to diagnose.
            return PublishResult.Undelivered("sign-in expired")
        }
        val token = config.authToken ?: run {
            Timber.i("No sync token; $what stays queued")
            return PublishResult.Undelivered("no token")
        }
        return try {
            val response = client.post(base + path) {
                header("Authorization", token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            // The body is read as text rather than decoded, because a failure
            // body is not the shape we asked for and decoding it would throw.
            val text = runCatching { response.body<String>() }.getOrNull()
            val result = classify(response.status, text)
            if (result is PublishResult.Undelivered) {
                Timber.w("Sync POST $what failed: ${result.reason}")
            } else {
                Timber.i("Sync POST $what: $result")
            }
            result
        } catch (e: Exception) {
            // Never propagates. The queue row stays PENDING and is retried; the
            // local leg is already recorded and is not affected either way.
            Timber.e(e, "Sync POST $what threw")
            PublishResult.Undelivered(e.javaClass.simpleName)
        }
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
     * ## The read path is where this actually bites
     *
     * Measured live 2026-10-13 against the deployed server. With a token whose
     * `exp` was 57.5 hours in the past:
     *
     * - a POST is refused `400 {"data":{},"message":"Failed to create record."}`,
     *   which is loud and lands in the outbox row's `lastError`;
     * - a GET returns **HTTP 200 with `totalItems: 0`**. PocketBase treats an
     *   expired token as an anonymous caller, and its list rules answer an
     *   anonymous list with an empty page rather than a 403.
     *
     * So the counterpart's phone pulls "zero transfers" and believes it. The
     * inbox looks refreshed, nothing errors, and a transfer the owner already
     * made never appears on the other phone. [TransferEventSync.pullEvents]
     * cannot tell that from a genuinely empty server.
     *
     * Refusing the request on a locally-known-dead token is what turns that into
     * a null, which [PullResult.Unreachable] already handles correctly by
     * leaving the inbox alone.
     *
     * @return false when the token is known dead. True otherwise, including
     *   when renewal failed because the phone is offline: that token may still
     *   have a day of life left, and only the server can refuse it.
     */
    private suspend fun ensureUsableToken(what: String): Boolean {
        val renewal = config.renewTokenIfNeeded()
        if (renewal is SyncConfig.Renewal.Expired) {
            Timber.w("Sync token expired; $what not attempted. ${renewal.reason}")
            return false
        }
        if (!config.isTokenUsable) {
            Timber.w("Sync token is past its expiry; $what not attempted")
            return false
        }
        return true
    }

    companion object {
        /**
         * The one place a publish outcome is decided, so the rule is stated once.
         *
         * A pure companion function rather than an inline branch in [post]
         * because it is pure policy over a status code and a body, and it is the
         * part that has to be right for responses no server in this project
         * returns on a normal day. That makes it testable exhaustively without
         * standing up a server, which matters more here than usual: this project
         * has already shipped the exact bug this function exists to get right.
         */
        fun classify(status: HttpStatusCode, body: String?): PublishResult = when {
            status == HttpStatusCode.OK || status == HttpStatusCode.Created ->
                PublishResult.Accepted

            // 400 from PocketBase on a create is a failed validation, and on
            // these two collections the only unique constraint is
            // correlationId. So this is the duplicate replay: the record exists
            // and it is the right one, which is what the queue was trying to
            // achieve. Only honoured when the body actually says so - see
            // `mentionsDuplicate`.
            status == HttpStatusCode.BadRequest && body.mentionsDuplicate() ->
                PublishResult.AlreadyPublished

            status == HttpStatusCode.Conflict -> PublishResult.AlreadyPublished

            else -> PublishResult.Undelivered("HTTP $status")
        }
    }
}

/**
 * Appends `"name":"value"` only when there is a value.
 *
 * The server's `fromAccountName` and `toAccountName` are optional, and
 * sending an explicit `null` is not the same as omitting them: PocketBase
 * validates a present-but-null text field differently from an absent one, and
 * the second is what "we do not know this name" is supposed to mean.
 */
private fun StringBuilder.appendNullable(name: String, value: String?) {
    if (value != null) {
        append(",\"$name\":\"${value.escape()}\"")
    }
}

/**
 * Minimal JSON string escape.
 *
 * Same rules as the one in `DirectoryAuthApi`, duplicated rather than shared
 * because that one is private to a file about credentials. A transfer
 * description, an account name and a currency are all free text a user can type,
 * and an unescaped quote in any of them would corrupt the body.
 */
internal fun String.escape(): String = buildString {
    for (c in this@escape) {
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

/**
 * Whether a failure body is the unique-constraint rejection.
 *
 * ## The code is `validation_not_unique`, and the difference is money
 *
 * Measured against the LIVE server 2026-10-13, a replayed POST on
 * transfer_events returns HTTP 400 with
 * `{"data":{"correlationId":{"code":"validation_not_unique","message":"Value
 * must be unique."}}}` and leaves exactly one row — the index
 * (idx_transfer_correlation) did its job. `validation_invalid_unique`, the
 * string this used to look for, is what PocketBase's *field-level*
 * `unique: true` produces, and this project has no field-level unique: the
 * constraint is real SQL in the `indexes` array, because `unique: true` is
 * silently ignored on 0.40. So `validation_invalid_unique` was a string this
 * server can never emit, and the check never fired.
 *
 * That is the worst possible miss for this particular function. The outbox
 * drain reads [AlreadyPublished] as "the server has it, clear the row"; every
 * other 400 becomes Undelivered and the row stays queued. So a replay whose
 * response was dropped — the retry case the idempotency index exists for —
 * was classified Undelivered("HTTP 400 ") and retried forever, on a transfer
 * that WAS already on the other phone. The row's `lastError` then read
 * "HTTP 400 ", which is precisely the unexplained dead end this card started
 * from. Both strings are accepted now, because being strict about a code the
 * server does not emit protects nothing and costs the ledger its retry.
 *
 * Still strict on the field: a 400 that does NOT name `correlationId` is some
 * other validation failure, and treating that as "already published" would
 * silently discard a transfer that was never sent — the exact failure this
 * whole design exists to prevent.
 */
private fun String?.mentionsDuplicate(): Boolean =
    this != null &&
        (
            contains("validation_not_unique", ignoreCase = true) ||
                contains("validation_invalid_unique", ignoreCase = true)
            ) &&
        contains("correlationId", ignoreCase = true)

package com.ivy.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wire: publishes transfers and confirmations, and reads back what the
 * counterpart's phone wrote.
 *
 * ## Why this is separate from [DirectorySyncApi]
 *
 * Two reasons, and the first is the load-bearing one.
 *
 * A POST and a GET do not fail the same way. A failed GET is harmless - the
 * cache simply keeps what it had. A failed POST is not: the request may or may
 * not have landed, and the caller cannot tell. So a publish cannot be a
 * `T?` that is null on any error; it has to distinguish "the server accepted
 * this", "the server already had this" and "we do not know", because the first
 * two clear the queue row and the third must leave it queued. Collapsing all
 * three into null is how a transfer ends up permanently stuck in the queue, or
 * worse, published twice.
 *
 * The second reason is that this needs the SAME failure tolerance as the
 * directory but a different one in the timing. [DirectorySyncApi] is allowed to
 * do nothing when sync is unconfigured. This is too - the local leg is already
 * written by the time any of this runs, and the queue row is what makes that
 * safe - but it must never THROW, because the drain runs in a coroutine that
 * nobody is watching.
 *
 * ## The three outcomes, and why the middle one is a success
 *
 * [PublishResult] has three cases, not two. `ALREADY_PUBLISHED` is a success:
 * the server rejected the POST because its unique index on `correlationId`
 * already holds this transfer, which means exactly one record exists and it is
 * the right one. That is the idempotency guarantee working. Treating it as a
 * failure would leave the row queued forever, retrying against a server that
 * will refuse it identically on every attempt - the transfer is on the other
 * phone and this device insists it is not.
 *
 * This is the exact shape of the bug that has already bitten this project: a
 * PocketBase collection with `unique: true` and no index behind it accepted a
 * replayed POST and wrote a second row. With a real index the replay is
 * refused, and refusing it must be understood as "already done".
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
 * PocketBase answers a duplicate create with HTTP 400 and a body naming the
 * field, e.g. `{"data":{"correlationId":{"code":"validation_invalid_unique"}}}`.
 * The check is deliberately loose on the surrounding JSON and strict on the one
 * token that matters: a 400 that does NOT look like this is a genuine
 * validation failure, and treating it as "already published" would silently
 * discard a transfer that was never sent - the exact failure this whole design
 * exists to prevent.
 */
private fun String?.mentionsDuplicate(): Boolean =
    this != null &&
        contains("validation_invalid_unique", ignoreCase = true) &&
        contains("correlationId", ignoreCase = true)

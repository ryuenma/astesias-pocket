package com.ivy.data.sync

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.Test

/**
 * Proves the rule that decides what a publish achieved.
 *
 * This is the one function in the transport where being wrong moves money twice
 * or loses it, so it is tested against every status the two collections can
 * return rather than only the happy one. The project has already shipped the
 * bug this guards: a PocketBase collection with `unique: true` and no index
 * behind it accepted a replayed POST and wrote a second row.
 */
class TransferSyncApiClassifyTest {

    /** What PocketBase sends back when the unique index rejects a replay. */
    private val duplicateBody =
        """{"status":400,"message":"Failed to create record.","data":""" +
            """{"correlationId":{"code":"validation_invalid_unique",""" +
            """"message":"The record has been rejected because it violates a """ +
            """unique constraint."}}}"""

    @Test
    fun `200 means the server took it`() {
        TransferSyncApi.classify(HttpStatusCode.OK, """{"id":"abc"}""") shouldBe
            TransferSyncApi.PublishResult.Accepted
    }

    @Test
    fun `201 also means the server took it`() {
        // PocketBase answers a create with 200 in the versions this project
        // deploys, but 201 is the correct status for a POST that made a
        // resource. Treating it as anything else would treat a successful
        // publish as a permanent failure and retry it forever.
        TransferSyncApi.classify(HttpStatusCode.Created, """{"id":"abc"}""") shouldBe
            TransferSyncApi.PublishResult.Accepted
    }

    @Test
    fun `a rejected duplicate is a SUCCESS because the record already exists`() {
        // THE load-bearing case. The server refused precisely because
        // correlationId is already there, which means one correct record exists
        // and the other phone already has it. Reporting this as a failure would
        // leave the row queued forever against a server that refuses it
        // identically on every attempt.
        TransferSyncApi.classify(HttpStatusCode.BadRequest, duplicateBody) shouldBe
            TransferSyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `the LIVE server's replay body is recognised`() {
        // THE regression test for the code-string miss, and the money-critical
        // one. Verbatim, measured against https://astesias-pb.fly.dev on
        // 2026-10-13 by POSTing the same correlationId twice: HTTP 400 with
        // `{"data":{"correlationId":{"code":"validation_not_unique",...}}}` and
        // exactly one row left behind - the index did its job.
        //
        // `validation_invalid_unique`, which the old check looked for, is what a
        // field-level `unique: true` produces, and this project has none (they
        // are silently ignored on 0.40; uniqueness is real SQL in the `indexes`
        // array). So the check never fired: a replay whose response was dropped
        // was classified Undelivered("HTTP 400 ") and retried FOREVER on a
        // transfer that was already on the other phone, with a lastError that
        // reads exactly like the card this came from.
        val liveBody = """{"data":{"correlationId":{"code":"validation_not_unique",""" +
            """"message":"Value must be unique."}},"message":"Failed to create """ +
            """record.","status":400}"""

        TransferSyncApi.classify(HttpStatusCode.BadRequest, liveBody) shouldBe
            TransferSyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `the field-level unique code is still recognised`() {
        // A PocketBase upgrade or a future `unique: true` would emit the other
        // string. Being strict about a code the server does not currently emit
        // protects nothing and costs the ledger its retry, so both are accepted.
        TransferSyncApi.classify(HttpStatusCode.BadRequest, duplicateBody) shouldBe
            TransferSyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `409 is also an already-published duplicate`() {
        TransferSyncApi.classify(HttpStatusCode.Conflict, null) shouldBe
            TransferSyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `a 400 that is NOT a duplicate stays a failure`() {
        // The dangerous neighbour. A validation failure for any other reason -
        // a missing required field, a bad amount - must NOT be read as "already
        // sent", or the transfer is discarded and the other phone never hears
        // about money that left an account.
        TransferSyncApi.classify(
            HttpStatusCode.BadRequest,
            """{"data":{"amount":{"code":"validation_invalid_number"}}}""",
        ) shouldBe TransferSyncApi.PublishResult.Undelivered("HTTP 400 Bad Request")
    }

    @Test
    fun `a 400 with no body at all stays a failure`() {
        // Unparseable is not the same as duplicate. Guessing "duplicate" here
        // would silently drop the transfer.
        TransferSyncApi.classify(HttpStatusCode.BadRequest, null) shouldBe
            TransferSyncApi.PublishResult.Undelivered("HTTP 400 Bad Request")
    }

    @Test
    fun `a 400 naming some other field is not a duplicate`() {
        // Both code strings are accepted now (see the LIVE body test), so the
        // FIELD is what still separates a replay from a real validation
        // failure. This is the assertion that keeps that true: a unique refusal
        // on some other column is not this index, and reading it as "already
        // sent" would silently discard a transfer that never arrived.
        TransferSyncApi.classify(
            HttpStatusCode.BadRequest,
            """{"data":{"recordedBy":{"code":"validation_not_unique"}}}""",
        ) shouldBe TransferSyncApi.PublishResult.Undelivered("HTTP 400 Bad Request")
    }

    @Test
    fun `401 is a failure so the row is retried after signing in again`() {
        TransferSyncApi.classify(HttpStatusCode.Unauthorized, null) shouldBe
            TransferSyncApi.PublishResult.Undelivered("HTTP 401 Unauthorized")
    }

    @Test
    fun `403 is a failure`() {
        TransferSyncApi.classify(HttpStatusCode.Forbidden, null) shouldBe
            TransferSyncApi.PublishResult.Undelivered("HTTP 403 Forbidden")
    }

    @Test
    fun `a 5xx is a failure and stays queued`() {
        TransferSyncApi.classify(HttpStatusCode.InternalServerError, "boom") shouldBe
            TransferSyncApi.PublishResult.Undelivered("HTTP 500 Internal Server Error")
    }

    @Test
    fun `the duplicate detection is case insensitive`() {
        // PocketBase's error codes are lower camel; being lenient about case
        // costs nothing and survives a server that changes its casing, whereas
        // being strict would turn a successful publish into a permanent failure.
        TransferSyncApi.classify(
            HttpStatusCode.BadRequest,
            """{"data":{"correlationId":{"code":"VALIDATION_INVALID_UNIQUE"}}}""",
        ) shouldBe TransferSyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `an undelivered result always explains itself`() {
        // The row records this string as its lastError, so an empty one would
        // leave a stuck transfer that nobody can diagnose.
        val result = TransferSyncApi.classify(HttpStatusCode.BadGateway, null)
        (result as TransferSyncApi.PublishResult.Undelivered).reason.isNotBlank() shouldBe true
    }
}
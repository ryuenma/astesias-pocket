package com.ivy.data.sync

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.Test

/**
 * Proves the rule that decides what a directory publish achieved.
 *
 * The status→outcome mapping is the one part of the write path that cannot be
 * exercised without a live server, and it is the part where being wrong is
 * invisible: a misread duplicate reports an account as visible on the other
 * phone when the server never stored it, and the picker shows nothing with no
 * error anywhere. So it is tested against every status this collection returns
 * rather than only the happy one.
 */
class DirectorySyncApiClassifyTest {

    /** What PocketBase sends back when the unique index rejects a re-publish. */
    private val duplicateBody =
        """{"status":400,"message":"Failed to create record.","data":""" +
            """{"profileId":{"code":"validation_invalid_unique",""" +
            """"message":"The record has been rejected because it violates a """ +
            """unique constraint."}}}"""

    @Test
    fun `200 means the server took it`() {
        DirectorySyncApi.classify(HttpStatusCode.OK, """{"id":"pbrec1"}""") shouldBe
            DirectorySyncApi.PublishResult.Accepted
    }

    @Test
    fun `201 also means the server took it`() {
        // 201 is the correct status for a POST that made a resource, and
        // treating it as a failure would retry a successful publish forever.
        DirectorySyncApi.classify(HttpStatusCode.Created, """{"id":"pbrec1"}""") shouldBe
            DirectorySyncApi.PublishResult.Accepted
    }

    @Test
    fun `a refused duplicate is a SUCCESS because the row already exists`() {
        // THE load-bearing case. The server refused precisely because
        // (profileId, localAccountId) is already taken, which means one correct
        // record exists and the other phone already sees it. Reporting this as a
        // failure would make every app start look broken.
        DirectorySyncApi.classify(HttpStatusCode.BadRequest, duplicateBody) shouldBe
            DirectorySyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `the LIVE server's duplicate body is recognised`() {
        // THE regression test for the code-string miss. Verbatim, measured
        // against https://astesias-pb.fly.dev on 2026-10-13 by posting the same
        // (profileId, localAccountId) twice. Note the code:
        // `validation_not_unique`, NOT `validation_invalid_unique` - the old
        // check looked only for the latter, which is what a field-level
        // `unique: true` produces, and this server has no field-level unique
        // (they are silently ignored on 0.40, so uniqueness is real SQL in the
        // `indexes` array). So the check never fired at all: a re-publish the
        // server already held came back Undelivered("HTTP 400") and counted as
        // undelivered on every sync, forever, while its row sat correct on the
        // server.
        val liveBody = """{"data":{"localAccountId":{"code":"validation_not_unique",""" +
            """"message":"Value must be unique."},"profileId":{"code":""" +
            """"validation_not_unique","message":"Value must be unique."}},""" +
            """"message":"Failed to create record.","status":400}"""

        DirectorySyncApi.classify(HttpStatusCode.BadRequest, liveBody) shouldBe
            DirectorySyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `a unique rejection naming only ONE indexed column is still a duplicate`() {
        // PocketBase names whichever column its query touched; on the live
        // server both were named, but a composite index may surface one. The
        // field check is an OR for exactly this reason.
        val oneColumn =
            """{"data":{"localAccountId":{"code":"validation_not_unique"}}}"""

        DirectorySyncApi.classify(HttpStatusCode.BadRequest, oneColumn) shouldBe
            DirectorySyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `the field-level unique code is still recognised`() {
        // A PocketBase upgrade or a future `unique: true` would emit the other
        // string. Being strict about a code the server does not currently emit
        // protects nothing, so both are accepted.
        DirectorySyncApi.classify(HttpStatusCode.BadRequest, duplicateBody) shouldBe
            DirectorySyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `409 is also an already-published duplicate`() {
        DirectorySyncApi.classify(HttpStatusCode.Conflict, null) shouldBe
            DirectorySyncApi.PublishResult.AlreadyPublished
    }

    @Test
    fun `a 400 that is NOT a duplicate stays undelivered`() {
        // The dangerous neighbour. A validation failure for any other reason -
        // a blank accountName, a missing required currency - means the row was
        // NOT stored. Calling it "already published" would claim an account is
        // on the other phone when it never got there.
        val blankName = """{"data":{"accountName":{"code":"validation_required"}}}"""
        val result = DirectorySyncApi.classify(HttpStatusCode.BadRequest, blankName)

        (result is DirectorySyncApi.PublishResult.Undelivered) shouldBe true
    }

    @Test
    fun `a 400 naming the unique code but no indexed field is still a failure`() {
        // The check is deliberately strict on the ONE token that identifies the
        // index. A body that says "unique constraint" without naming an indexed
        // column is some other collection's error or a changed schema, and
        // guessing would report an unpublished account as published.
        val body = """{"data":{"somethingElse":{"code":"validation_invalid_unique"}}}"""
        val result = DirectorySyncApi.classify(HttpStatusCode.BadRequest, body)

        (result is DirectorySyncApi.PublishResult.Undelivered) shouldBe true
    }

    @Test
    fun `a 403 from the create rule is undelivered, not a duplicate`() {
        // `@request.auth.profileId = @request.body.profileId` refusing a body
        // is a genuine failure: nothing was stored and the cause is a bug in
        // which profile this device thinks it is.
        DirectorySyncApi.classify(
            HttpStatusCode.Forbidden,
            """{"status":403,"message":"Only superusers can perform this action."}""",
        ) shouldBe DirectorySyncApi.PublishResult.Undelivered("HTTP 403 Forbidden")
    }

    @Test
    fun `a 401 is undelivered - an expired token is not a duplicate`() {
        DirectorySyncApi.classify(HttpStatusCode.Unauthorized, null) shouldBe
            DirectorySyncApi.PublishResult.Undelivered("HTTP 401 Unauthorized")
    }

    @Test
    fun `a 500 is undelivered and names the status`() {
        val result = DirectorySyncApi.classify(
            HttpStatusCode.InternalServerError,
            """{"status":500,"message":"Something went wrong."}""",
        )
        result shouldBe DirectorySyncApi.PublishResult.Undelivered(
            "HTTP 500 Internal Server Error",
        )
    }

    @Test
    fun `a null body on a 400 is not a duplicate`() {
        // No body means nothing identified this as the index rejection, so it
        // must not be reported as already-published.
        val result = DirectorySyncApi.classify(HttpStatusCode.BadRequest, null)
        (result is DirectorySyncApi.PublishResult.Undelivered) shouldBe true
    }
}
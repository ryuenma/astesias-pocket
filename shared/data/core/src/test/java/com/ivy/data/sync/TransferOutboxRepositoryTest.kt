package com.ivy.data.sync

import com.ivy.data.db.dao.read.TransferOutboxDao
import com.ivy.data.db.dao.write.WriteTransferOutboxDao
import com.ivy.data.db.entity.TransferOutboxEntity
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timber.log.Timber
import java.util.UUID

/**
 * Proves the invariants `TransferOutboxRepository`'s comments claim.
 *
 * A comment asserting "a duplicate is never posted twice" is worth nothing
 * unless something fails when it stops being true, so each rule below is a test.
 */
class TransferOutboxRepositoryTest {

    private val api: TransferSyncApi = mockk()
    private val dao: TransferOutboxDao = mockk(relaxed = true)
    private val writer: WriteTransferOutboxDao = mockk(relaxed = true)
    private val config: SyncConfig = mockk(relaxed = true)

    private val correlationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

    private fun repo() = TransferOutboxRepository(api, dao, writer, config)

    private fun queuedRow(
        id: UUID = correlationId,
        kind: String = TransferOutboxEntity.KIND_EVENT,
        amount: Double? = 500_000.0,
        decision: String? = null,
        toAccountId: UUID? = null,
        attempts: Int = 0,
    ) = TransferOutboxEntity(
        correlationId = id,
        kind = kind,
        recordedBy = "owner",
        amount = amount,
        currency = "IDR",
        occurredOn = "2026-10-05",
        fromAccountName = "BCA",
        toAccountName = "Mandiri",
        toAccountId = toAccountId,
        decision = decision,
        state = TransferOutboxEntity.STATE_PENDING,
        attempts = attempts,
        enqueuedAt = 1_757_030_400_000L,
    )

    // -----------------------------------------------------------------
    // Queueing: the local leg must never depend on this succeeding
    // -----------------------------------------------------------------

    @Test
    fun `queueing a transfer never touches the network`() = runTest {
        // THE central claim of the design. The owner has already paid; if
        // enqueueing reached for the network it could block, fail, or be
        // cancelled, and any of those would stop the other phone ever hearing
        // about it. Nothing here is allowed to call the API.
        coEvery { writer.enqueueIfAbsent(any()) } returns 1L

        repo().enqueueEvent(
            correlationId = correlationId,
            recordedBy = "owner",
            amount = 500_000.0,
            currency = "IDR",
            occurredOn = "2026-10-05",
            fromAccountName = "BCA",
            toAccountName = "Mandiri",
            enqueuedAt = 1_757_030_400_000L,
        ) shouldBe true

        coVerify(exactly = 0) { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.publishConfirmation(any(), any(), any()) }
    }

    @Test
    fun `queueing works even when sync is not configured`() = runTest {
        // She may sign in later. A transfer recorded before that must still be
        // waiting when she does, rather than having been silently dropped.
        coEvery { config.isConfigured } returns false
        coEvery { writer.enqueueIfAbsent(any()) } returns 1L

        repo().enqueueEvent(
            correlationId = correlationId,
            recordedBy = "owner",
            amount = 1.0,
            currency = "IDR",
            occurredOn = "2026-10-05",
            fromAccountName = "A",
            toAccountName = "B",
            enqueuedAt = 0L,
        ) shouldBe true
    }

    @Test
    fun `the queued row carries exactly what the wire contract requires`() = runTest {
        coEvery { writer.enqueueIfAbsent(any()) } returns 1L

        repo().enqueueEvent(
            correlationId = correlationId,
            recordedBy = "owner",
            amount = 500_000.0,
            currency = "IDR",
            occurredOn = "2026-10-05",
            fromAccountName = "BCA",
            toAccountName = "Mandiri",
            enqueuedAt = 1_757_030_400_000L,
        )

        val written = slot<TransferOutboxEntity>()
        coVerify { writer.enqueueIfAbsent(capture(written)) }
        written.captured.let {
            it.correlationId shouldBe correlationId
            it.kind shouldBe TransferOutboxEntity.KIND_EVENT
            it.recordedBy shouldBe "owner"
            it.amount shouldBe 500_000.0
            it.currency shouldBe "IDR"
            it.occurredOn shouldBe "2026-10-05"
            it.fromAccountName shouldBe "BCA"
            it.toAccountName shouldBe "Mandiri"
            it.state shouldBe TransferOutboxEntity.STATE_PENDING
        }
    }

    @Test
    fun `queueing the same transfer twice yields one row`() = runTest {
        // The primary key refuses the second. Room reports 0, which is the
        // replay signal - and it is reported as such rather than as a failure,
        // because a retried save is not an error.
        coEvery { writer.enqueueIfAbsent(any()) } returns 0L

        repo().enqueueEvent(
            correlationId = correlationId,
            recordedBy = "owner",
            amount = 1.0,
            currency = "IDR",
            occurredOn = "2026-10-05",
            fromAccountName = "A",
            toAccountName = "B",
            enqueuedAt = 0L,
        ) shouldBe false
    }

    @Test
    fun `a queued confirmation takes no money`() = runTest {
        coEvery { writer.enqueueIfAbsent(any()) } returns 1L

        repo().enqueueConfirmation(
            correlationId = correlationId,
            decision = TransferOutboxEntity.DECISION_ACCEPTED,
            enqueuedAt = 0L,
        ) shouldBe true

        val written = slot<TransferOutboxEntity>()
        coVerify { writer.enqueueIfAbsent(capture(written)) }
        written.captured.let {
            it.kind shouldBe TransferOutboxEntity.KIND_CONFIRMATION
            it.decision shouldBe "ACCEPTED"
            // A confirmation carries an answer, not an amount. Leaving these set
            // would put a number on the wire that the server has no field for.
            it.amount shouldBe null
            it.currency shouldBe null
        }
    }

    @Test
    fun `a confirmation is attributed to this device and never to the sender`() = runTest {
        // The server's rule is `@request.auth.profileId = @request.body.decidedBy`,
        // so `decidedBy` must be the profile whose TOKEN is posting. An id taken
        // from the caller could be the other person's, which the server refuses
        // and which would be an attempt to answer on someone else's behalf.
        coEvery { config.profileId } returns "so"
        coEvery { writer.enqueueIfAbsent(any()) } returns 1L

        repo().enqueueConfirmation(
            correlationId = correlationId,
            decision = TransferOutboxEntity.DECISION_DECLINED,
            enqueuedAt = 0L,
        )

        val written = slot<TransferOutboxEntity>()
        coVerify { writer.enqueueIfAbsent(capture(written)) }
        written.captured.recordedBy shouldBe "so"
    }

    // -----------------------------------------------------------------
    // Draining: publishing what was queued
    // -----------------------------------------------------------------

    @Test
    fun `a drain of an empty queue touches nothing`() = runTest {
        coEvery { dao.findPending(any()) } returns emptyList()

        val result = repo().drain()

        result.published shouldBe 0
        result.complete shouldBe true
        coVerify(exactly = 0) { writer.markSent(any(), any(), any(), any()) }
        coVerify(exactly = 0) { writer.enqueueIfAbsent(any()) }
    }

    @Test
    fun `an accepted publish marks the row sent`() = runTest {
        coEvery { dao.findPending(any()) } returns listOf(queuedRow())
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Accepted
        coEvery { writer.markSent(any(), any(), any(), any()) } returns 1

        val result = repo().drain()

        result.published shouldBe 1
        result.deferred shouldBe 0
        coVerify { writer.markSent(correlationId, any(), any(), any()) }
    }

    @Test
    fun `an already-published duplicate CLEARS the row and is reported as sent`() = runTest {
        // THE bug this whole queue exists to make impossible. The server refused
        // because its unique index says this transfer is already recorded, which
        // means the other phone already has it. Clearing the row is what stops
        // this device re-sending it on every sync forever.
        coEvery { dao.findPending(any()) } returns listOf(queuedRow())
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.AlreadyPublished
        coEvery { writer.markSent(any(), any(), any(), any()) } returns 1

        val result = repo().drain()

        result.deferred shouldBe 0
        result.complete shouldBe true
        // Counted apart from `published`: from the owner's point of view the
        // transfer is delivered either way, but a device that keeps hitting
        // AlreadyPublished on everything is a device that is behind, and folding
        // the two together would hide that.
        result.alreadyThere shouldBe 1
        result.published shouldBe 0
        coVerify { writer.markSent(correlationId, any(), any(), any()) }
        coVerify(exactly = 0) { writer.recordFailure(any(), any(), any()) }
    }

    @Test
    fun `an undelivered publish LEAVES the row queued and records why`() = runTest {
        // A phone in a lift. The leg is already in the ledger, the row is intact,
        // and the next drain retries. Nothing here may mark it sent.
        coEvery { dao.findPending(any()) } returns listOf(queuedRow())
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Undelivered("HTTP 503")

        val result = repo().drain()

        result.deferred shouldBe 1
        result.complete shouldBe false
        coVerify(exactly = 0) { writer.markSent(any(), any(), any(), any()) }
        coVerify { writer.recordFailure(correlationId, "HTTP 503", any()) }
    }

    // -----------------------------------------------------------------
    // Permanent refusals: the server said no, so it will say no again
    // -----------------------------------------------------------------

    @Test
    fun `a permanent refusal is counted, not merely retried`() = runTest {
        // 400-validation, 403 and 422 are not "we do not know". The same row
        // will be refused identically next time, so an unbounded retry is not
        // safety - it is a row that owns a slot in every drain forever. It has
        // to be COUNTED, and a transient network failure must not be.
        coEvery { dao.findPending(any()) } returns listOf(queuedRow(attempts = 0))
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Rejected("HTTP 422")

        val result = repo().drain()

        result.rejected shouldBe 1
        result.deferred shouldBe 0
        coVerify { writer.recordRejection(correlationId, "HTTP 422") }
        coVerify(exactly = 0) { writer.recordFailure(any(), any(), any()) }
        coVerify(exactly = 0) { writer.markSent(any(), any(), any(), any()) }
        // One refusal short of the cap: still retryable, so not terminal yet.
        coVerify(exactly = 0) { writer.markFailed(any()) }
    }

    @Test
    fun `a refusal at the cap is marked FAILED so it stops owning a batch slot`() = runTest {
        // The starvation mechanism: findPending is ORDER BY enqueuedAt ASC
        // LIMIT 25, so a permanently refused row keeps the head of the queue
        // until something takes it out of PENDING. FAILED is that exit, and the
        // row is kept rather than deleted because isOwnEvent() reads it.
        coEvery { dao.findPending(any()) } returns
            listOf(queuedRow(attempts = TransferOutboxEntity.MAX_REJECTIONS - 1))
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Rejected("HTTP 403")

        val result = repo().drain()

        result.rejected shouldBe 1
        coVerify { writer.recordRejection(correlationId, "HTTP 403") }
        coVerify { writer.markFailed(correlationId) }
        coVerify(exactly = 0) { writer.markSent(any(), any(), any(), any()) }
    }

    @Test
    fun `a transient failure never counts toward the cap, however often it repeats`() = runTest {
        // THE regression this split exists to prevent. Offline, timeout, an
        // expired token and "sync not configured" all mean "we do not know yet",
        // and a phone that queues transfers BEFORE signing in must not walk into
        // a terminal state: there is an existing test proving that queueing
        // without sync configured is supported. Counting these would have
        // turned valid queued money permanently failed inside a day.
        coEvery { dao.findPending(any()) } returns
            listOf(queuedRow(attempts = TransferOutboxEntity.MAX_REJECTIONS + 40))
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Undelivered("sign-in expired")

        repeat(3) { repo().drain() }

        coVerify(exactly = 3) { writer.recordFailure(correlationId, "sign-in expired", any()) }
        coVerify(exactly = 0) { writer.recordRejection(any(), any()) }
        coVerify(exactly = 0) { writer.markFailed(any()) }
    }

    @Test
    fun `one undelivered row does not strand the rest of the queue`() = runTest {
        // A single transfer the server always refuses must not block every
        // transfer recorded after it. Otherwise one bad row is a permanent
        // outage for the other phone.
        val second = UUID.fromString("11111111-bbbb-cccc-dddd-eeeeeeeeeeee")
        coEvery { dao.findPending(any()) } returns listOf(
            queuedRow(),
            queuedRow(id = second),
        )
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Undelivered("HTTP 503")
        coEvery { writer.markSent(any(), any(), any(), any()) } returns 1

        val result = repo().drain()

        // Both were attempted; the drain examined every row it was given.
        coVerify(exactly = 2) { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) }
        result.deferred shouldBe 2
    }

    @Test
    fun `one failure among successes still publishes the rest`() = runTest {
        val second = UUID.fromString("11111111-bbbb-cccc-dddd-eeeeeeeeeeee")
        coEvery { dao.findPending(any()) } returns listOf(
            queuedRow(),
            queuedRow(id = second),
        )
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Accepted
        coEvery { writer.markSent(any(), any(), any(), any()) } returns 1

        val result = repo().drain()

        result.published shouldBe 2
        result.deferred shouldBe 0
    }

    @Test
    fun `a drain loses no race against a concurrent drain`() = runTest {
        // markSent is guarded on PENDING, so the second drain's update matches no
        // row. It must not then claim it published something, or the reported
        // total double-counts.
        coEvery { dao.findPending(any()) } returns listOf(queuedRow())
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Accepted
        coEvery { writer.markSent(any(), any(), any(), any()) } returns 0

        val result = repo().drain()

        result.published shouldBe 0
        result.alreadyThere shouldBe 1
    }

    @Test
    fun `a queued event with no amount is refused rather than posted as zero`() = runTest {
        // Posting 0.00 would be a lie about money, and the other phone would
        // offer to accept it.
        coEvery { dao.findPending(any()) } returns listOf(queuedRow(amount = null))
        coEvery { writer.recordFailure(any(), any(), any()) } returns 1

        val result = repo().drain()

        result.rejected shouldBe 1
        coVerify(exactly = 0) { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify { writer.recordRejection(correlationId, match { it.contains("no amount") }) }
    }

    @Test
    fun `a queued confirmation with no decision is refused`() = runTest {
        // The server field is a select of exactly two values. An empty string is
        // not one of them, so posting it would be refused as invalid and retried
        // forever.
        coEvery { dao.findPending(any()) } returns listOf(
            queuedRow(kind = TransferOutboxEntity.KIND_CONFIRMATION, amount = null, decision = null)
        )
        coEvery { writer.recordFailure(any(), any(), any()) } returns 1

        val result = repo().drain()

        result.rejected shouldBe 1
        coVerify(exactly = 0) { api.publishConfirmation(any(), any(), any()) }
    }

    @Test
    fun `a row of unknown kind is not published and not guessed at`() = runTest {
        coEvery { dao.findPending(any()) } returns listOf(queuedRow(kind = "SOMETHING_ELSE"))
        coEvery { writer.recordFailure(any(), any(), any()) } returns 1

        val result = repo().drain()

        result.rejected shouldBe 1
        coVerify(exactly = 0) { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.publishConfirmation(any(), any(), any()) }
    }

    @Test
    fun `a drained event carries the queued directory id on the wire unchanged`() = runTest {
        // The point of the id is that it is the ONE identifier both ends agree
        // on. A drain that reformatted it, or substituted a locally-derived one,
        // would hand the receiver an id she cannot resolve and land her back in
        // the exact bug the id exists to fix.
        val destination = UUID.fromString("22222222-2222-2222-2222-222222222222")
        coEvery { dao.findPending(any()) } returns listOf(queuedRow(toAccountId = destination))
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Accepted
        coEvery { writer.markSent(any(), any(), any(), any()) } returns 1

        val result = repo().drain()

        result.published shouldBe 1
        coVerify {
            api.publishEvent(
                correlationId = correlationId.toString(),
                recordedBy = "owner",
                amount = 500_000.0,
                currency = "IDR",
                occurredOn = "2026-10-05",
                fromAccountName = "BCA",
                toAccountName = "Mandiri",
                toAccountId = destination.toString(),
            )
        }
    }

    @Test
    fun `a drain passes the queue through to the wire unchanged`() = runTest {
        // Asserted on the arguments rather than the outcome: a drain that
        // reformatted the date or renamed an account would send a transfer that
        // does not match what was recorded.
        coEvery { dao.findPending(any()) } returns listOf(queuedRow())
        coEvery { api.publishEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Accepted
        coEvery { writer.markSent(any(), any(), any(), any()) } returns 1

        repo().drain()

        coVerify {
            api.publishEvent(
                correlationId = correlationId.toString(),
                recordedBy = "owner",
                amount = 500_000.0,
                currency = "IDR",
                occurredOn = "2026-10-05",
                fromAccountName = "BCA",
                toAccountName = "Mandiri",
                // A row recorded before ids existed carries no id on the wire:
                // omitted, not empty, exactly as [appendNullable] renders it.
                toAccountId = null,
            )
        }
    }

    @Test
    fun `a queued confirmation is published with only the wire fields`() = runTest {
        // decidedBy comes from config at PUBLISH time, not at queue time, so the
        // stub has to be in place before the drain runs.
        coEvery { config.profileId } returns "so"
        coEvery { dao.findPending(any()) } returns listOf(
            queuedRow(
                kind = TransferOutboxEntity.KIND_CONFIRMATION,
                amount = null,
                decision = TransferOutboxEntity.DECISION_ACCEPTED,
            ).copy(recordedBy = "so")
        )
        coEvery { api.publishConfirmation(any(), any(), any()) } returns
            TransferSyncApi.PublishResult.Accepted
        coEvery { writer.markSent(any(), any(), any(), any()) } returns 1

        repo().drain()

        coVerify {
            api.publishConfirmation(
                correlationId = correlationId.toString(),
                decidedBy = "so",
                decision = "ACCEPTED",
            )
        }
    }

    @Test
    fun `a row this device queued is recognised as its own`() = runTest {
        // The inbound side relies on this to avoid mirroring our own transfer
        // into our own inbox.
        coEvery { dao.findState(correlationId) } returns TransferOutboxEntity.STATE_SENT

        repo().isOwnEvent(correlationId) shouldBe true
    }

    @Test
    fun `an unknown correlation id is not ours`() = runTest {
        coEvery { dao.findState(any()) } returns null

        repo().isOwnEvent(correlationId) shouldBe false
    }

    @Test
    fun `touches no Timber tree that does not exist`() {
        // Timber with no tree planted is a no-op; the drain must not assume a
        // logger is installed, or a unit test run would crash on Timber.w.
        Timber.e(Exception("boom"), "logging with no tree planted")
    }
}

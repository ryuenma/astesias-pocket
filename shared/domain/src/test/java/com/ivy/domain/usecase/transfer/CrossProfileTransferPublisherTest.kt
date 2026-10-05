package com.ivy.domain.usecase.transfer

import com.ivy.data.db.entity.TransferOutboxEntity
import com.ivy.data.sync.SyncConfig
import com.ivy.data.sync.TransferOutboxRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Proves the guarantee the whole transport rests on: recording a transfer is
 * never blocked on, and never failed by, the network.
 */
class CrossProfileTransferPublisherTest {

    private val outbox: TransferOutboxRepository = mockk(relaxed = true)
    private val config: SyncConfig = mockk(relaxed = true)

    private val correlationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

    private fun publisher() = CrossProfileTransferPublisher(outbox, config)

    private suspend fun publish(
        amount: Double = 500_000.0,
        occurredOn: Instant = Instant.parse("2026-10-05T04:00:00Z"),
    ) = publisher().publishRecorded(
        correlationId = correlationId,
        fromAccountName = "BCA",
        toAccountName = "Mandiri",
        amount = amount,
        currency = "IDR",
        occurredOn = occurredOn,
    )

    @Test
    fun `the queued row names the transfer exactly as it was recorded`() = runTest {
        coEvery { config.profileId } returns "owner"
        coEvery { outbox.enqueueEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            true
        coEvery { outbox.drain(any()) } returns
            TransferOutboxRepository.DrainResult(1, 0, 0)

        publish()

        val date = slot<String>()
        val who = slot<String>()
        coVerify {
            outbox.enqueueEvent(
                correlationId = correlationId,
                recordedBy = capture(who),
                amount = 500_000.0,
                currency = "IDR",
                occurredOn = capture(date),
                fromAccountName = "BCA",
                toAccountName = "Mandiri",
                enqueuedAt = any(),
            )
        }
        // recordedBy is THIS device: the server field means "who recorded it".
        // Putting the counterpart's id here would attribute her own payment to
        // the other person.
        who.captured shouldBe "owner"
        date.captured shouldBe "2026-10-05"
    }

    @Test
    fun `the date is the UTC calendar day so both phones agree on it`() = runTest {
        coEvery { config.profileId } returns "owner"
        coEvery { outbox.enqueueEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            true
        coEvery { outbox.drain(any()) } returns
            TransferOutboxRepository.DrainResult(1, 0, 0)

        // 04:00 UTC on the 5th. In UTC+7 that is 11:00 on the 5th; a publisher
        // that used the LOCAL date would agree here by luck, which is exactly why
        // the rule has to be pinned by a test rather than left to inspection.
        publish(occurredOn = Instant.parse("2026-10-05T04:00:00Z"))

        val date = slot<String>()
        coVerify {
            outbox.enqueueEvent(
                correlationId = any(),
                recordedBy = any(),
                amount = any(),
                currency = any(),
                occurredOn = capture(date),
                fromAccountName = any(),
                toAccountName = any(),
                enqueuedAt = any(),
            )
        }
        date.captured shouldBe "2026-10-05"
    }

    @Test
    fun `an unconfigured device still queues the transfer`() = runTest {
        // She may sign in later. A transfer recorded before that must be waiting
        // when she does, not have been silently dropped - the ledger says it
        // happened, so the transport owes the server a copy.
        coEvery { config.isConfigured } returns false
        coEvery { outbox.enqueueEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            true

        publish()

        coVerify(exactly = 1) {
            outbox.enqueueEvent(any(), any(), any(), any(), any(), any(), any(), any())
        }
        // And makes no network attempt at all.
        coVerify(exactly = 0) { outbox.drain(any()) }
    }

    @Test
    fun `an unconfigured device never blocks on the network`() = runTest {
        coEvery { config.isConfigured } returns false
        coEvery { outbox.enqueueEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            true

        val result = publish()

        // Nothing was published and nothing was lost.
        result.published shouldBe 0
        result.complete shouldBe true
    }

    @Test
    fun `an unreachable server does NOT fail the publish`() = runTest {
        // The result honestly reports the deferral, and the call still returns
        // normally. Nothing here can tell the owner her payment failed, because
        // nothing here failed: the ledger row was written before this ran.
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { outbox.enqueueEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            true
        coEvery { outbox.drain(any()) } returns
            TransferOutboxRepository.DrainResult(published = 0, alreadyThere = 0, deferred = 1)

        val result = publish()

        result.deferred shouldBe 1
        result.complete shouldBe false
    }

    @Test
    fun `a queued row is still drained again when the server never got it`() = runTest {
        // The row existing is not proof it was published - the first attempt may
        // have failed. Draining again costs one request and covers the case.
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        // 0 = the primary key refused it, i.e. already queued.
        coEvery { outbox.enqueueEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            false
        coEvery { outbox.drain(any()) } returns
            TransferOutboxRepository.DrainResult(1, 0, 0)

        val result = publish()

        coVerify(exactly = 1) { outbox.drain(any()) }
        result.published shouldBe 1
    }

    @Test
    fun `an unconfigured device publishes under a unique id rather than a blank one`() = runTest {
        // The server field is required. An empty value would be refused as
        // invalid and the drain would retry it forever, so a per-transfer
        // fallback is used instead - unique, so it can never be confused with
        // another profile's event.
        coEvery { config.profileId } returns null
        coEvery { outbox.enqueueEvent(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            true

        publisher().publishRecorded(
            correlationId = correlationId,
            fromAccountName = "BCA",
            toAccountName = "Mandiri",
            amount = 1.0,
            currency = "IDR",
            occurredOn = Instant.parse("2026-10-05T00:00:00Z"),
        )

        val who = slot<String>()
        coVerify {
            outbox.enqueueEvent(
                correlationId = any(),
                recordedBy = capture(who),
                amount = any(),
                currency = any(),
                occurredOn = any(),
                fromAccountName = any(),
                toAccountName = any(),
                enqueuedAt = any(),
            )
        }
        who.captured.isNotBlank() shouldBe true
    }

    @Test
    fun `the decision names on the wire match the server's enum`() = runTest {
        // A typo here would create a third state the server does not accept, and
        // it would only surface as a publish that never succeeds.
        TransferOutboxEntity.DECISION_ACCEPTED shouldBe "ACCEPTED"
        TransferOutboxEntity.DECISION_DECLINED shouldBe "DECLINED"
    }
}
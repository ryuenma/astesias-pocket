package com.ivy.domain.usecase.transfer

import com.ivy.data.db.dao.read.PendingTransferDao
import com.ivy.data.db.entity.PendingTransferEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.db.transfer.TransferDecisionWriter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
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
 * The decide path had NO test at all, which is how it shipped a bug in which the
 * inbox row was marked ACCEPTED before the ledger row was written: a failed insert
 * left a decided transfer with no transaction behind it, and nothing to retry.
 *
 * These tests pin the shape of the fix - one atomic write through
 * [TransferDecisionWriter], never two unwrapped statements - and the reporting
 * contract: a write that throws must reach the caller instead of being reported as
 * a decision.
 *
 * They do NOT prove Room's rollback. That needs an in-memory database, and this
 * repo has no Robolectric harness (adding one is a separate decision). What is
 * proved here is that the code has exactly one place to be atomic, and that this
 * layer cannot pretend a failed write succeeded.
 */
class PendingTransferRepositoryDecideTest {

    private val dao: PendingTransferDao = mockk(relaxed = true)
    private val writer: TransferDecisionWriter = mockk(relaxed = true)
    private val repository = PendingTransferRepository(dao = dao, decisionWriter = writer)

    private val correlationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val legId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val intoAccountId = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val senderAccountId = UUID.fromString("33333333-3333-4333-8333-333333333333")

    private val pendingRow = PendingTransferEntity(
        correlationId = correlationId,
        recordedByProfileId = "owner",
        recordedByProfileName = "Kiki",
        intoAccountId = intoAccountId,
        fromAccountName = "BCA",
        amount = 25_000.0,
        currency = "IDR",
        occurredOn = Instant.parse("2026-11-01T04:00:00Z"),
        title = "Lunch",
        description = null,
        state = "PENDING",
    )

    private fun givenPendingTransfer() {
        coEvery { dao.findState(correlationId) } returns "PENDING"
        coEvery { dao.findByCorrelationId(correlationId) } returns pendingRow
    }

    @Test
    fun `accept performs ONE write that carries the ledger leg`() = runTest {
        givenPendingTransfer()
        val leg = slot<TransactionEntity>()
        coEvery {
            writer.decideAndWrite(any(), any(), capture(leg))
        } returns true

        val decision = repository.decide(
            correlationId = correlationId,
            action = TransferInbox.Action.ACCEPT,
            confirmedLegId = legId,
            senderAccountId = senderAccountId,
        )

        decision shouldBe TransferInbox.Decision.Accept
        coVerify(exactly = 1) { writer.decideAndWrite(correlationId, "ACCEPTED", any()) }
        // The old path issued these two separately, which is the bug: nothing tied
        // the state change to the row it was supposed to describe.
        coVerify(exactly = 0) { dao.markDecided(any(), any()) }
        leg.captured.shouldNotBeNull()
        leg.captured.id shouldBe legId
    }

    @Test
    fun `decline asks for no ledger leg and never touches the owner's side`() = runTest {
        givenPendingTransfer()
        coEvery { writer.decideAndWrite(any(), any(), any()) } returns true

        repository.decide(
            correlationId = correlationId,
            action = TransferInbox.Action.DECLINE,
            confirmedLegId = legId,
            senderAccountId = senderAccountId,
        ) shouldBe TransferInbox.Decision.Decline

        // Declining cannot un-send money, so her ledger must stay untouched.
        coVerify(exactly = 1) { writer.decideAndWrite(correlationId, "DECLINED", null) }
    }

    @Test
    fun `a failed write throws instead of being reported as a decision`() = runTest {
        givenPendingTransfer()
        coEvery { writer.decideAndWrite(any(), any(), any()) } throws
            IllegalStateException("database is locked")

        shouldThrow<IllegalStateException> {
            repository.decide(
                correlationId = correlationId,
                action = TransferInbox.Action.ACCEPT,
                confirmedLegId = legId,
                senderAccountId = senderAccountId,
            )
        }
    }

    @Test
    fun `a second tap on an already-decided transfer writes nothing`() = runTest {
        givenPendingTransfer()
        coEvery { writer.decideAndWrite(any(), any(), any()) } returns false

        repository.decide(
            correlationId = correlationId,
            action = TransferInbox.Action.ACCEPT,
            confirmedLegId = legId,
            senderAccountId = senderAccountId,
        ) shouldBe TransferInbox.Decision.NoChange
    }
}

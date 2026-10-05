package com.ivy.domain.usecase.transfer

import com.ivy.data.db.dao.read.TransferConfirmationDao
import com.ivy.data.db.dao.write.WriteTransferConfirmationDao
import com.ivy.data.db.entity.TransferConfirmationEntity
import com.ivy.data.db.entity.TransferConfirmationWithEvent
import com.ivy.data.db.entity.TransferOutboxEntity
import com.ivy.data.sync.SyncConfig
import com.ivy.data.sync.TransferOutboxRepository
import com.ivy.data.sync.TransferSyncApi
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timber.log.Timber
import java.util.UUID

/**
 * Proves the rule this repository exists to enforce: the counterpart's answer
 * about money THIS device sent is persisted, and nothing else is.
 *
 * A comment asserting "the answer is recorded" is worth nothing unless something
 * fails when it stops being true, so each rule below is a test. The centre of
 * gravity is the refusals: the alternative to each one is a durable row in the
 * owner's own database asserting something about her money that is not true.
 */
class TransferConfirmationRepositoryTest {

    private val api: TransferSyncApi = mockk()
    private val dao: TransferConfirmationDao = mockk(relaxed = true)
    private val writer: WriteTransferConfirmationDao = mockk(relaxed = true)
    private val outbox: TransferOutboxRepository = mockk(relaxed = true)
    private val config: SyncConfig = mockk(relaxed = true)

    private val correlationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

    private fun repo() = TransferConfirmationRepository(api, dao, writer, outbox, config)

    private fun remote(
        correlationId: String = this.correlationId.toString(),
        decidedBy: String = "so",
        decision: String = "ACCEPTED",
    ) = TransferSyncApi.RemoteConfirmation(
        correlationId = correlationId,
        decidedBy = decidedBy,
        decision = decision,
    )

    /** The retained outbox row for a transfer this device sent. */
    private fun ownEventRow(
        id: UUID = correlationId,
        kind: String = TransferOutboxEntity.KIND_EVENT,
    ) = TransferOutboxEntity(
        correlationId = id,
        kind = kind,
        recordedBy = "owner",
        amount = 500_000.0,
        currency = "IDR",
        occurredOn = "2026-10-05",
        fromAccountName = "BCA",
        toAccountName = "Mandiri",
        state = TransferOutboxEntity.STATE_SENT,
        enqueuedAt = 1_757_030_400_000L,
    )

    private fun stubConfigured() {
        coEvery { config.isConfigured } returns true
    }

    private fun stubServer(vararg items: TransferSyncApi.RemoteConfirmation) {
        coEvery { api.fetchConfirmations() } returns TransferSyncApi.PbList(items.toList(), items.size)
    }

    // -----------------------------------------------------------------
    // Recording: the answer actually lands
    // -----------------------------------------------------------------

    @Test
    fun `an answer about our own transfer IS persisted`() = runTest {
        // The bug this whole class fixes. Before, the answer was fetched and
        // discarded: the owner sent money and never found out it arrived.
        stubConfigured()
        stubServer(remote())
        coEvery { outbox.ownEvent(correlationId) } returns ownEventRow()
        coEvery { writer.recordIfAbsent(any()) } returns 1L

        val result = repo().pullAnswers()

        result shouldBe TransferConfirmationRepository.AnswerPullResult.Pulled(
            recorded = 1,
            alreadyKnown = 0,
            notOurs = 0,
            unusable = 0,
        )

        val written = slot<TransferConfirmationEntity>()
        coVerify { writer.recordIfAbsent(capture(written)) }
        written.captured.let {
            it.correlationId shouldBe correlationId
            it.decision shouldBe TransferOutboxEntity.DECISION_ACCEPTED
            it.decidedBy shouldBe "so"
            // Never read: an answer the owner has not opened is exactly the
            // state the Settings badge counts. Recording it as already-seen
            // would make the row permanently invisible.
            it.acknowledged shouldBe false
        }
    }

    @Test
    fun `a DECLINED answer is persisted as declined`() = runTest {
        // Not normalised, not softened, and certainly not turned into an
        // accepted transfer. The money left her account either way and she is
        // entitled to the answer the partner actually gave.
        stubConfigured()
        stubServer(remote(decision = TransferOutboxEntity.DECISION_DECLINED))
        coEvery { outbox.ownEvent(correlationId) } returns ownEventRow()
        coEvery { writer.recordIfAbsent(any()) } returns 1L

        repo().pullAnswers()

        val written = slot<TransferConfirmationEntity>()
        coVerify { writer.recordIfAbsent(capture(written)) }
        written.captured.decision shouldBe TransferOutboxEntity.DECISION_DECLINED
    }

    @Test
    fun `the answer is recorded under the transfer's OWN correlation id`() = runTest {
        // The join key. It is the only thing the two phones agree on - the two
        // legs live in two databases and share no account id - so recording it
        // under anything else would produce a row nothing can ever be matched to.
        val id = UUID.fromString("11111111-2222-4333-8444-555555555555")
        stubConfigured()
        stubServer(remote(correlationId = id.toString()))
        coEvery { outbox.ownEvent(id) } returns ownEventRow(id = id)
        coEvery { writer.recordIfAbsent(any()) } returns 1L

        repo().pullAnswers()

        val written = slot<TransferConfirmationEntity>()
        coVerify { writer.recordIfAbsent(capture(written)) }
        written.captured.correlationId shouldBe id
    }

    // -----------------------------------------------------------------
    // Not ours: the owner's own answer coming back
    // -----------------------------------------------------------------

    @Test
    fun `an answer about a transfer this device never sent is NOT recorded`() = runTest {
        // Every profile may read every confirmation, so this device sees the
        // partner's answers to their OWN transfers in the same feed. Recording
        // one would put a durable claim in the owner's database about money she
        // did not send.
        stubConfigured()
        stubServer(remote())
        coEvery { outbox.ownEvent(correlationId) } returns null

        val result = repo().pullAnswers()

        (result as TransferConfirmationRepository.AnswerPullResult.Pulled).notOurs shouldBe 1
        coVerify(exactly = 0) { writer.recordIfAbsent(any()) }
    }

    @Test
    fun `our OWN published answer coming back is NOT recorded`() = runTest {
        // The second half of the same rule, and the one with the real hole. This
        // device retains outbox rows of kind CONFIRMATION - the answers SHE gave
        // about transfers that arrived here. An incoming answer matching one of
        // those keys concerns somebody else's transfer, and recording it would
        // tell her the partner accepted something she had merely received.
        stubConfigured()
        stubServer(remote())
        coEvery { outbox.ownEvent(correlationId) } returns
            ownEventRow(kind = TransferOutboxEntity.KIND_CONFIRMATION)

        val result = repo().pullAnswers()

        (result as TransferConfirmationRepository.AnswerPullResult.Pulled).notOurs shouldBe 1
        coVerify(exactly = 0) { writer.recordIfAbsent(any()) }
    }

    // -----------------------------------------------------------------
    // Refusals: never record something we cannot interpret
    // -----------------------------------------------------------------

    @Test
    fun `an unparseable correlation id is dropped rather than guessed`() = runTest {
        // Without a usable key there is nothing to join on and nothing to make
        // idempotent, so there is nowhere to store it at all.
        stubConfigured()
        stubServer(remote(correlationId = "not-a-uuid"))

        val result = repo().pullAnswers()

        (result as TransferConfirmationRepository.AnswerPullResult.Pulled).unusable shouldBe 1
        coVerify(exactly = 0) { writer.recordIfAbsent(any()) }
    }

    @Test
    fun `an unrecognised decision is dropped rather than defaulted`() = runTest {
        // ACCEPTED and DECLINED mean opposite things. There is no third reading of
        // "maybe" that is safer than storing it, and a default would be a claim
        // about money that the partner never made.
        stubConfigured()
        stubServer(remote(decision = "MAYBE"))
        coEvery { outbox.ownEvent(correlationId) } returns ownEventRow()

        val result = repo().pullAnswers()

        (result as TransferConfirmationRepository.AnswerPullResult.Pulled).unusable shouldBe 1
        coVerify(exactly = 0) { writer.recordIfAbsent(any()) }
    }

    @Test
    fun `an empty decision is dropped`() = runTest {
        // The wire field is a String defaulting to "", so this is what a server
        // that omitted the field looks like. Not a decision.
        stubConfigured()
        stubServer(remote(decision = ""))
        coEvery { outbox.ownEvent(correlationId) } returns ownEventRow()

        val result = repo().pullAnswers()

        (result as TransferConfirmationRepository.AnswerPullResult.Pulled).unusable shouldBe 1
        coVerify(exactly = 0) { writer.recordIfAbsent(any()) }
    }

    // -----------------------------------------------------------------
    // Idempotency: a second sync must not undo the first
    // -----------------------------------------------------------------

    @Test
    fun `a redelivered answer is a replay and is NOT written twice`() = runTest {
        // The primary key refuses it. Reported as a replay rather than a failure,
        // and deliberately NOT retried with REPLACE - that would write
        // acknowledged = 0 over a row she has already read, so the badge would
        // light up again on every sync and she could never clear it.
        stubConfigured()
        stubServer(remote())
        coEvery { outbox.ownEvent(correlationId) } returns ownEventRow()
        coEvery { writer.recordIfAbsent(any()) } returns 0L

        val result = repo().pullAnswers()

        (result as TransferConfirmationRepository.AnswerPullResult.Pulled).alreadyKnown shouldBe 1
        (result as TransferConfirmationRepository.AnswerPullResult.Pulled).recorded shouldBe 0
        coVerify(exactly = 1) { writer.recordIfAbsent(any()) }
    }

    @Test
    fun `two syncs over the same answers leave exactly one row`() = runTest {
        // The end-to-end idempotency claim, which is the property the badge
        // depends on: the second pull must not produce a second row.
        stubConfigured()
        stubServer(remote())
        coEvery { outbox.ownEvent(correlationId) } returns ownEventRow()
        // First insert lands, the second is refused by the key.
        var inserts = 0
        coEvery { writer.recordIfAbsent(any()) } answers {
            inserts++
            if (inserts == 1) 1L else 0L
        }

        repo().pullAnswers()
        repo().pullAnswers()

        coVerify(exactly = 2) { writer.recordIfAbsent(any()) }
    }

    @Test
    fun `one answer that throws does not cost her the others`() = runTest {
        // Per-answer isolation. A locked database on the first row must not
        // abandon the four answers queued behind it that were perfectly fine.
        val good = UUID.fromString("11111111-2222-4333-8444-555555555555")
        stubConfigured()
        stubServer(remote(), remote(correlationId = good.toString()))
        coEvery { outbox.ownEvent(any()) } returns ownEventRow()
        // One stub with a call counter: re-stubbing REPLACES the earlier answer
        // rather than adding a second attempt to it.
        var attempts = 0
        coEvery { writer.recordIfAbsent(any()) } answers {
            attempts++
            if (attempts == 1) error("database is locked") else 1L
        }

        val result = runCatching { repo().pullAnswers() }

        result.isFailure shouldBe false
        (result.getOrNull() as TransferConfirmationRepository.AnswerPullResult.Pulled).recorded shouldBe 1
    }

    // -----------------------------------------------------------------
    // Unreachable: the recorded answers must survive a failed sync
    // -----------------------------------------------------------------

    @Test
    fun `an unconfigured device does not even read the server`() = runTest {
        coEvery { config.isConfigured } returns false

        repo().pullAnswers() shouldBe TransferConfirmationRepository.AnswerPullResult.NotConfigured

        coVerify(exactly = 0) { api.fetchConfirmations() }
        coVerify(exactly = 0) { writer.recordIfAbsent(any()) }
    }

    @Test
    fun `an unreachable server leaves the recorded answers exactly as they were`() = runTest {
        // Not emptied. An empty list claiming she has had no answers is a lie;
        // the durable record is the whole reason this exists.
        stubConfigured()
        coEvery { api.fetchConfirmations() } returns null

        repo().pullAnswers() shouldBe TransferConfirmationRepository.AnswerPullResult.Unreachable

        coVerify(exactly = 0) { writer.recordIfAbsent(any()) }
        coVerify(exactly = 0) { writer.acknowledgeAll() }
    }

    @Test
    fun `a failing read is reported as unreachable rather than thrown`() = runTest {
        // `fetchConfirmations` is specified never to throw, and this must not
        // depend on that promise being kept by every implementation.
        stubConfigured()
        coEvery { api.fetchConfirmations() } throws IllegalStateException("socket died")

        val result = runCatching { repo().pullAnswers() }

        result.isFailure shouldBe false
        result.getOrNull()
            .shouldBeInstanceOf<TransferConfirmationRepository.AnswerPullResult.Unreachable>()
    }

    // -----------------------------------------------------------------
    // Reading it back, and acknowledging
    // -----------------------------------------------------------------

    @Test
    fun `the list is read from the DATABASE and not from the pull envelope`() = runTest {
        // The load-bearing property of persisting rather than toasting. The
        // answer has to still be here after the sync that delivered it is gone -
        // the app closed, the queue drained, a week passed. A screen that read
        // the envelope would be empty on every entry after the first.
        coEvery { dao.findAllWithEvent() } returns listOf(
            TransferConfirmationWithEvent(
                correlationId = correlationId,
                decision = TransferOutboxEntity.DECISION_ACCEPTED,
                decidedBy = "so",
                recordedAt = 1_757_030_400_000L,
                acknowledged = false,
                amount = 500_000.0,
                currency = "IDR",
                occurredOn = "2026-10-05",
                fromAccountName = "BCA",
                toAccountName = "Mandiri",
            )
        )

        val rows = repo().answered()

        rows.single().let {
            it.decision shouldBe TransferOutboxEntity.DECISION_ACCEPTED
            it.amount shouldBe 500_000.0
            it.fromAccountName shouldBe "BCA"
        }
        // And no network call was involved in producing it.
        coVerify(exactly = 0) { api.fetchConfirmations() }
    }

    @Test
    fun `an answer joins to the transfer it is about so the money can be shown`() = runTest {
        // The join exists so this row can name the amount and accounts without
        // the confirmation table carrying a second copy of them.
        coEvery { dao.findAllWithEvent() } returns listOf(
            TransferConfirmationWithEvent(
                correlationId = correlationId,
                decision = TransferOutboxEntity.DECISION_DECLINED,
                decidedBy = "so",
                recordedAt = 1_757_030_400_000L,
                acknowledged = true,
                amount = 250_000.0,
                currency = "IDR",
                occurredOn = "2026-10-05",
                fromAccountName = "BCA",
                toAccountName = "Mandiri",
            )
        )

        repo().answered().single().let {
            it.amount shouldBe 250_000.0
            it.currency shouldBe "IDR"
            it.toAccountName shouldBe "Mandiri"
        }
    }

    @Test
    fun `the unread count is the count of unacknowledged answers`() = runTest {
        // Drives the Settings badge, so a disagreement with the list would be
        // worse than no badge at all.
        coEvery { dao.countUnacknowledged() } returns 3

        repo().unacknowledgedCount() shouldBe 3
    }

    @Test
    fun `acknowledging marks every answer read but DELETES nothing`() = runTest {
        // The answers are a record of what happened to her money, and "what did
        // she say about the transfer I sent on the 5th" is a question she can ask
        // again next month. Opening the list marks them read; it does not erase
        // them.
        coEvery { writer.acknowledgeAll() } returns 3

        repo().acknowledgeAll() shouldBe 3

        coVerify(exactly = 1) { writer.acknowledgeAll() }
        coVerify(exactly = 0) { writer.deleteAll() }
    }

    @Test
    fun `touches no Timber tree that does not exist`() {
        // The pull must not assume a logger is installed, or a unit test run
        // would crash on Timber.w with no tree planted.
        Timber.e(Exception("boom"), "logging with no tree planted")
    }
}

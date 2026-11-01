package com.ivy.domain.usecase.transfer

import com.ivy.data.sync.DirectorySyncRepository
import com.ivy.data.sync.SyncConfig
import com.ivy.data.sync.TransferOutboxRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TransferSyncRunnerTest {

    private val config: SyncConfig = mockk(relaxed = true)
    private val directorySync: DirectorySyncRepository = mockk(relaxed = true)
    private val outbox: TransferOutboxRepository = mockk(relaxed = true)
    private val eventSync: TransferEventSync = mockk(relaxed = true)

    private fun runner() = TransferSyncRunner(
        config = config,
        directorySync = directorySync,
        outbox = outbox,
        eventSync = eventSync,
    )

    @Test
    fun `unconfigured device skips all network steps`() = runTest {
        every { config.isConfigured } returns false

        runner().runOnce().ran shouldBe false

        coVerify(exactly = 0) { directorySync.publishLocalAccounts() }
        coVerify(exactly = 0) { directorySync.refresh() }
        coVerify(exactly = 0) { outbox.drain() }
        coVerify(exactly = 0) { eventSync.pullEvents() }
        coVerify(exactly = 0) { eventSync.pullConfirmations() }
    }

    @Test
    fun `configured device runs directory sync, drains outbox before pulling, and isolates failures`() = runTest {
        every { config.isConfigured } returns true
        coEvery { directorySync.refresh() } throws RuntimeException("flaky network")

        runner().runOnce().ran shouldBe true

        coVerifyOrder {
            directorySync.publishLocalAccounts()
            directorySync.refresh()
            outbox.drain()
            eventSync.pullEvents()
            eventSync.pullConfirmations()
        }
    }

    @Test
    fun `a pass reports how many transfers and answers actually arrived`() = runTest {
        // The counts are the whole reason this returns a report instead of a
        // Boolean: a caller cannot decide whether to notify anyone without them.
        every { config.isConfigured } returns true
        coEvery { eventSync.pullEvents() } returns
            TransferEventSync.PullResult.Pulled(newItems = 3, alreadyKnown = 1, undeliverable = 0)
        coEvery { eventSync.pullConfirmations() } returns
            TransferConfirmationRepository.AnswerPullResult.Pulled(
                recorded = 2,
                alreadyKnown = 0,
                notOurs = 1,
                unusable = 0,
            )

        val report = runner().runOnce()

        report.ran shouldBe true
        report.newTransfers shouldBe 3
        report.newAnswers shouldBe 2
    }

    @Test
    fun `a failed pull reports nothing arrived rather than a wrong count`() = runTest {
        // A notification saying "a transfer is waiting" about a pass that never
        // read the server would be a lie the owner cannot check.
        every { config.isConfigured } returns true
        coEvery { eventSync.pullEvents() } throws RuntimeException("socket closed")
        coEvery { eventSync.pullConfirmations() } returns
            TransferConfirmationRepository.AnswerPullResult.Unreachable

        val report = runner().runOnce()

        report.ran shouldBe true
        report.newTransfers shouldBe 0
        report.newAnswers shouldBe 0
    }
}

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

        runner().runOnce() shouldBe false

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

        runner().runOnce() shouldBe true

        coVerifyOrder {
            directorySync.publishLocalAccounts()
            directorySync.refresh()
            outbox.drain()
            eventSync.pullEvents()
            eventSync.pullConfirmations()
        }
    }
}

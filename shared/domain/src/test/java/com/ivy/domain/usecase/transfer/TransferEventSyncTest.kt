package com.ivy.domain.usecase.transfer

import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.PendingTransferDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.sync.DirectorySyncApi
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
import java.time.Instant
import java.util.UUID

/**
 * Proves the inbound rules `TransferEventSync`'s comments claim.
 *
 * The centre of gravity is the refusals. Every one of them exists because the
 * alternative is an inbox entry that looks real and is not, and she would then
 * accept money into an account the transfer never named.
 */
class TransferEventSyncTest {

    private val api: TransferSyncApi = mockk()
    private val profilesApi: DirectorySyncApi = mockk()
    private val outbox: TransferOutboxRepository = mockk(relaxed = true)
    private val pendingDao: PendingTransferDao = mockk(relaxed = true)
    private val transactionDao: WriteTransactionDao = mockk(relaxed = true)
    private val accounts: AccountDao = mockk()
    private val config: SyncConfig = mockk(relaxed = true)

    private val herAccount = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val correlationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

    private val answers: TransferConfirmationRepository = mockk(relaxed = true)

    private val pendingTransfers = PendingTransferRepository(pendingDao, transactionDao)

    private fun sync() = TransferEventSync(
        api = api,
        profilesApi = profilesApi,
        outbox = outbox,
        pendingTransfers = pendingTransfers,
        answers = answers,
        accounts = accounts,
        config = config,
    )

    private fun event(
        correlationId: String = this.correlationId.toString(),
        recordedBy: String = "so",
        toAccountName: String? = "Mandiri",
        fromAccountName: String? = "BCA",
        occurredOn: String = "2026-10-05",
        amount: Double = 500_000.0,
    ) = TransferSyncApi.RemoteTransferEvent(
        correlationId = correlationId,
        recordedBy = recordedBy,
        amount = amount,
        currency = "IDR",
        occurredOn = occurredOn,
        fromAccountName = fromAccountName,
        toAccountName = toAccountName,
    )

    private fun herAccountEntity(name: String = "Mandiri") = AccountEntity(
        id = herAccount,
        name = name,
        color = 0,
    )

    private fun stubConfigured() {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { profilesApi.fetchProfiles() } returns null
        coEvery { outbox.isOwnEvent(any()) } returns false
    }

    // -----------------------------------------------------------------
    // Arriving
    // -----------------------------------------------------------------

    @Test
    fun `an event from the other profile becomes a PENDING inbox item`() = runTest {
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(listOf(event()), 1)
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()
        coEvery { pendingDao.findState(any()) } returns null

        val result = sync().pullEvents()

        result shouldBe TransferEventSync.PullResult.Pulled(
            newItems = 1,
            alreadyKnown = 0,
            undeliverable = 0,
        )

        val offered = slot<com.ivy.data.db.entity.PendingTransferEntity>()
        coVerify { pendingDao.insertIfAbsent(capture(offered)) }
        offered.captured.let {
            it.correlationId shouldBe correlationId
            it.recordedByProfileId shouldBe "so"
            // PENDING, never ACCEPTED. The whole point of the inbox is that
            // nothing is decided on her behalf.
            it.state shouldBe PendingTransferState.PENDING.name
            it.intoAccountId shouldBe herAccount
            it.fromAccountName shouldBe "BCA"
            it.amount shouldBe 500_000.0
            it.currency shouldBe "IDR"
        }
    }

    @Test
    fun `an event NEVER writes a transaction`() = runTest {
        // The load-bearing negative. Money leaving his account is a fact; money
        // entering hers is a decision only she can make. A transport that wrote
        // the mirrored leg on delivery would be reporting money into an account
        // nobody agreed to receive it in.
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(listOf(event()), 1)
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()
        coEvery { pendingDao.findState(any()) } returns null

        sync().pullEvents()

        coVerify(exactly = 0) { transactionDao.save(any()) }
    }

    @Test
    fun `our own event is not mirrored into our own inbox`() = runTest {
        // Both profiles may read every event, so this device necessarily sees
        // what it published. Without this check our own transfer comes back in
        // the same feed and lands in her inbox as a second mirrored copy.
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { profilesApi.fetchProfiles() } returns null
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(
            listOf(event(recordedBy = "owner")),
            1,
        )
        coEvery { outbox.isOwnEvent(any()) } returns false

        val result = sync().pullEvents()

        (result as TransferEventSync.PullResult.Pulled).alreadyKnown shouldBe 1
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
        coVerify(exactly = 0) { accounts.findByName(any()) }
    }

    @Test
    fun `an event we already published is skipped even when recordedBy is not ours`() = runTest {
        // The second of the two own-event checks, and the one that has to keep
        // working: the outbox knows even if the recordedBy attribution does not.
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { profilesApi.fetchProfiles() } returns null
        coEvery { outbox.isOwnEvent(correlationId) } returns true
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(listOf(event()), 1)

        sync().pullEvents()

        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `a replayed event does not become a second inbox row`() = runTest {
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(listOf(event()), 1)
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()
        // Already known and still PENDING: a replay, which changes nothing.
        coEvery { pendingDao.findState(correlationId) } returns PendingTransferState.PENDING.name

        val result = sync().pullEvents()

        (result as TransferEventSync.PullResult.Pulled).alreadyKnown shouldBe 1
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `a replayed event does NOT resurrect a transfer she already declined`() = runTest {
        // The reason the tombstone is retained. A declined transfer re-delivered
        // would otherwise come back into her inbox on every sync, with nothing
        // left saying she had already said no.
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(listOf(event()), 1)
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()
        coEvery { pendingDao.findState(correlationId) } returns PendingTransferState.DECLINED.name

        sync().pullEvents()

        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    // -----------------------------------------------------------------
    // Refusals: never guess
    // -----------------------------------------------------------------

    @Test
    fun `an event with no matching account here is NOT offered`() = runTest {
        // His account id does not exist in this database, so the destination
        // arrives as a name. If no local account carries it, accepting the item
        // would put money somewhere she never chose.
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(listOf(event()), 1)
        coEvery { accounts.findByName("Mandiri") } returns null

        val result = sync().pullEvents()

        (result as TransferEventSync.PullResult.Pulled).undeliverable shouldBe 1
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `an event with no destination name is NOT offered`() = runTest {
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(
            listOf(event(toAccountName = null)),
            1,
        )

        val result = sync().pullEvents()

        (result as TransferEventSync.PullResult.Pulled).undeliverable shouldBe 1
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `an event with no source name is NOT offered`() = runTest {
        // A blank "from" would render as money from nowhere.
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(
            listOf(event(fromAccountName = null)),
            1,
        )
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()

        val result = sync().pullEvents()

        (result as TransferEventSync.PullResult.Pulled).undeliverable shouldBe 1
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `an event with an unparseable correlation id is dropped`() = runTest {
        // Without a usable key there is nothing to join on and nothing to make
        // idempotent, so it cannot be stored at all.
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(
            listOf(event(correlationId = "not-a-uuid")),
            1,
        )

        val result = sync().pullEvents()

        (result as TransferEventSync.PullResult.Pulled).undeliverable shouldBe 1
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `an event with an unparseable date is dropped rather than defaulted`() = runTest {
        // Defaulting would silently move a transfer to a day nobody chose.
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(
            listOf(event(occurredOn = "05/10/2026")),
            1,
        )
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()

        val result = sync().pullEvents()

        (result as TransferEventSync.PullResult.Pulled).undeliverable shouldBe 1
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `an event with no recordedBy is dropped`() = runTest {
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(
            listOf(event(recordedBy = "")),
            1,
        )

        val result = sync().pullEvents()

        (result as TransferEventSync.PullResult.Pulled).undeliverable shouldBe 1
    }

    // -----------------------------------------------------------------
    // The date, and the owner name
    // -----------------------------------------------------------------

    @Test
    fun `the date arrives as the calendar day the owner chose`() = runTest {
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(
            listOf(event(occurredOn = "2026-10-05")),
            1,
        )
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()
        coEvery { pendingDao.findState(any()) } returns null

        sync().pullEvents()

        val offered = slot<com.ivy.data.db.entity.PendingTransferEntity>()
        coVerify { pendingDao.insertIfAbsent(capture(offered)) }
        // UTC midnight: the one reading both phones agree on, so the transfer
        // cannot appear on a different day for a reader east or west of
        // Greenwich.
        offered.captured.occurredOn shouldBe Instant.parse("2026-10-05T00:00:00Z")
    }

    @Test
    fun `a known profile name is shown instead of the raw id`() = runTest {
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { outbox.isOwnEvent(any()) } returns false
        coEvery { profilesApi.fetchProfiles() } returns DirectorySyncApi.PbList(
            listOf(DirectorySyncApi.RemoteProfile(profileId = "so", displayName = "Raka")),
            1,
        )
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(listOf(event()), 1)
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()
        coEvery { pendingDao.findState(any()) } returns null

        sync().pullEvents()

        val offered = slot<com.ivy.data.db.entity.PendingTransferEntity>()
        coVerify { pendingDao.insertIfAbsent(capture(offered)) }
        offered.captured.recordedByProfileName shouldBe "Raka"
    }

    @Test
    fun `an unknown profile falls back to the id rather than to blank`() = runTest {
        // Blank would read as "a transfer from nobody".
        stubConfigured()
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(listOf(event()), 1)
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()
        coEvery { pendingDao.findState(any()) } returns null

        sync().pullEvents()

        val offered = slot<com.ivy.data.db.entity.PendingTransferEntity>()
        coVerify { pendingDao.insertIfAbsent(capture(offered)) }
        offered.captured.recordedByProfileName shouldBe "so"
    }

    // -----------------------------------------------------------------
    // Unreachable
    // -----------------------------------------------------------------

    @Test
    fun `an unconfigured device does not even read the server`() = runTest {
        coEvery { config.isConfigured } returns false

        sync().pullEvents() shouldBe TransferEventSync.PullResult.NotConfigured

        coVerify(exactly = 0) { api.fetchEvents() }
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `an unreachable server leaves the inbox exactly as it was`() = runTest {
        // Not emptied. A stale inbox showing three items is honest; an empty one
        // claiming she has nothing pending is a lie.
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchEvents() } returns null

        sync().pullEvents() shouldBe TransferEventSync.PullResult.Unreachable

        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
        coVerify(exactly = 0) { transactionDao.save(any()) }
    }

    @Test
    fun `confirmations are PERSISTED by the answer repository rather than returned`() = runTest {
        // This method used to hand back a List<RemoteConfirmation> that the
        // caller discarded, which is the whole bug: the answer was fetched,
        // decoded and thrown away on every sync. It now reports what the
        // repository recorded, and returns no list at all - so there is nothing
        // left to discard.
        coEvery { answers.pullAnswers() } returns
            TransferConfirmationRepository.AnswerPullResult.Pulled(
                recorded = 1,
                alreadyKnown = 0,
                notOurs = 0,
                unusable = 0,
            )

        val result = sync().pullConfirmations()

        result shouldBe TransferConfirmationRepository.AnswerPullResult.Pulled(
            recorded = 1,
            alreadyKnown = 0,
            notOurs = 0,
            unusable = 0,
        )
        coVerify(exactly = 1) { answers.pullAnswers() }
    }

    @Test
    fun `pulling confirmations NEVER touches the ledger`() = runTest {
        // The owner's leg is NOT rewritten by anything she decides. Her money
        // moved when she saved it, and the partner's answer is knowledge about
        // that fact, never a write to it.
        coEvery { answers.pullAnswers() } returns
            TransferConfirmationRepository.AnswerPullResult.Unreachable

        sync().pullConfirmations()

        coVerify(exactly = 0) { transactionDao.save(any()) }
        coVerify(exactly = 0) { pendingDao.insertIfAbsent(any()) }
    }

    @Test
    fun `an unconfigured device reports NotConfigured without reading the server`() = runTest {
        // Delegated rather than re-decided here: the repository owns the
        // isConfigured rule, and a second copy of it here could disagree with the
        // first. Asserted on the RETURN, which is the part that must not lie.
        coEvery { answers.pullAnswers() } returns
            TransferConfirmationRepository.AnswerPullResult.NotConfigured

        sync().pullConfirmations() shouldBe
            TransferConfirmationRepository.AnswerPullResult.NotConfigured
    }

    @Test
    fun `a failing read is reported as unreachable rather than thrown`() = runTest {
        // The counterpart's inbox is the least valuable thing on the device to
        // lose in a stack trace. `fetchEvents` is specified never to throw, but
        // the sync must not depend on that promise being kept by every
        // implementation of the api.
        coEvery { config.isConfigured } returns true
        coEvery { api.fetchEvents() } throws IllegalStateException("socket died")

        val result = runCatching { sync().pullEvents() }

        result.isFailure shouldBe false
        result.getOrNull().shouldBeInstanceOf<TransferEventSync.PullResult.Unreachable>()
    }

    @Test
    fun `one event that throws does not cost her the others`() = runTest {
        // Per-event isolation, so a single poisoned row cannot silently swallow
        // the four transfers behind it that were perfectly deliverable.
        val good = UUID.fromString("11111111-bbbb-cccc-dddd-eeeeeeeeeeee")
        coEvery { config.isConfigured } returns true
        coEvery { config.profileId } returns "owner"
        coEvery { profilesApi.fetchProfiles() } returns null
        coEvery { outbox.isOwnEvent(any()) } returns false
        coEvery { api.fetchEvents() } returns TransferSyncApi.PbList(
            listOf(
                event(correlationId = correlationId.toString()),
                event(correlationId = good.toString()),
            ),
            2,
        )
        coEvery { accounts.findByName("Mandiri") } returns herAccountEntity()
        coEvery { pendingDao.findState(any()) } returns null
        // The inbox refuses the FIRST one outright and then behaves. One stub
        // with a call counter, because re-stubbing replaces the earlier answer
        // rather than adding a second attempt to it.
        var attempts = 0
        coEvery { pendingDao.insertIfAbsent(any()) } answers {
            attempts++
            if (attempts == 1) error("database is locked")
            1L
        }

        val result = runCatching { sync().pullEvents() }

        result.isFailure shouldBe false
        (result.getOrNull() as TransferEventSync.PullResult.Pulled).newItems shouldBe 1
    }

    @Test
    fun `touches no Timber tree that does not exist`() {
        Timber.e(Exception("boom"), "logging with no tree planted")
    }
}
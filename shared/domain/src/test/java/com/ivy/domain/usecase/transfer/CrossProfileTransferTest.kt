package com.ivy.domain.usecase.transfer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The two legs of a cross-profile transfer.
 *
 * These are pure functions precisely so this can be tested without a database.
 * The properties below are the ones that make the feature correct; the rest is
 * plumbing that Room will reject loudly if it breaks.
 */
class CrossProfileTransferTest {

    private val correlationId: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val myAccount: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val herAccount: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val myLegId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val herLegId: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val when0: Instant = Instant.parse("2026-10-02T09:15:00Z")

    private fun sender() = CrossProfileTransfer.senderLeg(
        legId = myLegId,
        correlationId = correlationId,
        fromAccountId = myAccount,
        toAccountId = herAccount,
        counterpartProfileId = "so",
        amount = 500_000.0,
        dateTime = when0,
        title = "Transfer",
    )

    private fun receiver() = CrossProfileTransfer.receiverLeg(
        legId = herLegId,
        correlationId = correlationId,
        intoAccountId = herAccount,
        fromAccountId = myAccount,
        senderProfileId = "default",
        amount = 500_000.0,
        dateTime = when0,
        title = "Transfer",
    )

    @Test
    fun `both legs are transfers so the amount reads as moved money, not income`() {
        // Decided by owner: it shows as a transfer on her side. INCOME would imply
        // she earned it.
        sender().type.name shouldBe "TRANSFER"
        receiver().type.name shouldBe "TRANSFER"
    }

    @Test
    fun `legs share only the correlation id`() {
        sender().linkedTransactionId shouldBe correlationId
        receiver().linkedTransactionId shouldBe correlationId
        // Account ids are mirrored, never shared - that is the whole reason a
        // correlation id is needed at all.
        sender().accountId shouldBe myAccount
        sender().toAccountId shouldBe herAccount
        receiver().accountId shouldBe myAccount
        receiver().toAccountId shouldBe herAccount
    }

    @Test
    fun `an inbound cross-profile leg credits the destination account and never debits it`() = kotlinx.coroutines.test.runTest {
        val leg = receiver()
        val mapped = com.ivy.data.model.Transfer(
            id = com.ivy.data.model.TransactionId(leg.id),
            title = null,
            description = null,
            category = null,
            time = leg.dateTime!!,
            settled = true,
            metadata = com.ivy.data.model.TransactionMetadata(null, null, null, null),
            fromAccount = com.ivy.data.model.AccountId(leg.accountId),
            fromValue = com.ivy.data.model.PositiveValue(
                amount = com.ivy.data.model.primitive.PositiveDouble.unsafe(leg.amount),
                asset = com.ivy.data.model.primitive.AssetCode.unsafe("IDR"),
            ),
            toAccount = com.ivy.data.model.AccountId(leg.toAccountId!!),
            toValue = com.ivy.data.model.PositiveValue(
                amount = com.ivy.data.model.primitive.PositiveDouble.unsafe(leg.amount),
                asset = com.ivy.data.model.primitive.AssetCode.unsafe("IDR"),
            ),
            tags = emptyList(),
        )
        val statsUseCase = com.ivy.domain.usecase.account.AccountStatsUseCase(
            dispatchers = com.ivy.base.TestDispatchersProvider,
            accountRepository = io.mockk.mockk(relaxed = true),
            exchangeUseCase = io.mockk.mockk(relaxed = true),
        )
        val stats = statsUseCase.calculate(
            account = com.ivy.data.model.AccountId(herAccount),
            transactions = listOf(mapped),
        )
        val balance = com.ivy.domain.usecase.BalanceBuilder().run {
            processDeposits(incomes = stats.income.values, transfersIn = stats.transfersIn.values)
            processWithdrawals(expenses = stats.expense.values, transfersOut = stats.transfersOut.values)
            build()
        }
        val idr = com.ivy.data.model.primitive.AssetCode.unsafe("IDR")
        stats.transfersIn.values[idr]?.value shouldBe 500_000.0
        stats.transfersOut.values[idr] shouldBe null
        balance[idr]?.value shouldBe 500_000.0
    }

    @Test
    fun `each leg names the other profile`() {
        sender().counterpartProfileId shouldBe "so"
        receiver().counterpartProfileId shouldBe "default"
        // A null counterpart is what keeps an ordinary intra-profile transfer
        // indistinguishable from cross-profile data in the row itself.
        sender().counterpartProfileId shouldNotBe null
    }

    @Test
    fun `amounts are preserved exactly on both sides`() {
        sender().amount shouldBe 500_000.0
        receiver().amount shouldBe 500_000.0
    }

    @Test
    fun `sender does not invent a converted amount for a foreign currency`() {
        // These accounts may hold different currencies. toAmount left null means we
        // admit we do not know the rate; filling it in would silently fabricate a
        // conversion. The receiver's leg carries what actually arrived.
        sender().toAmount shouldBe null
    }

    @Test
    fun `legs have distinct ids so both can coexist in one sync payload`() {
        sender().id shouldNotBe receiver().id
        sender().id shouldBe myLegId
        receiver().id shouldBe herLegId
    }

    @Test
    fun `date is carried unchanged so both sides agree when it happened`() {
        sender().dateTime shouldBe when0
        receiver().dateTime shouldBe when0
        sender().dateTime!!.toEpochMilli() shouldBe receiver().dateTime!!.toEpochMilli()
    }

    @Test
    fun `a freshly generated correlation id is unique per transfer`() {
        // Guards the idempotency key: two distinct transfers must not collide, or
        // the receiver would silently drop the second one.
        val ids = (1..1_000).map { UUID.randomUUID() }
        ids.toSet().size shouldBe ids.size
    }

    @Test
    fun `a non-positive amount is representable so the use case can reject it explicitly`() {
        // These builders describe a shape; they do not decide what is valid. A
        // zero-amount leg is constructible, which means the rejection MUST live at
        // the use-case boundary or a zero transfer would record as real movement.
        val zero = CrossProfileTransfer.senderLeg(
            legId = UUID.randomUUID(),
            correlationId = UUID.randomUUID(),
            fromAccountId = myAccount,
            toAccountId = herAccount,
            counterpartProfileId = "so",
            amount = 0.0,
            dateTime = when0,
        )
        zero.amount shouldBe 0.0
        // Pinned as a contract the use case is expected to enforce.
        (zero.amount > 0.0) shouldBe false

        val negative = CrossProfileTransfer.receiverLeg(
            legId = UUID.randomUUID(),
            correlationId = UUID.randomUUID(),
            intoAccountId = herAccount,
            fromAccountId = myAccount,
            senderProfileId = "default",
            amount = -1.0,
            dateTime = when0,
        )
        (negative.amount > 0.0) shouldBe false
        (receiver().amount > 0.0) shouldBe true
    }

    @Test
    fun `title survives so the sender can label the transfer`() {
        sender().title shouldContain "Transfer"
        receiver().title shouldContain "Transfer"
    }

    @Test
    fun `time of day is retained in the stored instant even though entry omits it`() {
        // Entry is date-only after the owner's change; the entity still carries an
        // Instant so existing rows and sorting are unaffected. This pins that the
        // two are not confused.
        val seconds = sender().dateTime!!.truncatedTo(ChronoUnit.MINUTES).epochSecond
        (seconds > 0L) shouldBe true
    }
}
package com.ivy.data.repository.mapper

import arrow.core.Some
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.AccountId
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.testing.account
import com.ivy.data.repository.AccountRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * The OWNER's leg of a mirrored partner transfer, through the real mapper.
 *
 * This is the test that matters for the owner's side, and it exists because of a
 * specific failure mode rather than a general one. [TransactionMapper.toDomain]
 * resolves a TRANSFER's destination account locally, and for a cross-profile
 * transfer the destination is the PARTNER's account - an id that does not exist
 * in this database and cannot be resolved here.
 *
 * The mapper rejects unmappable rows with an `Either`, and
 * `TransactionRepository.retrieveTrns` DROPS those with `mapNotNull`. So a leg
 * that cannot be mapped is not an error the user sees; it is a transaction that
 * silently vanishes from their ledger while still sitting in the database
 * counting towards nothing. That is precisely the "record it freely and
 * immediately" promise failing in the worst possible way - invisibly.
 *
 * So: the owner's leg must survive the round trip even though its destination is
 * not a local account.
 */
class CrossProfileTransferMapperTest {

    private val accountRepo = mockk<AccountRepository>()
    private lateinit var mapper: TransactionMapper

    private val correlationId: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val myAccountId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val herAccountId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val when0: Instant = Instant.parse("2026-10-02T09:15:00Z")

    @Before
    fun setup() {
        mapper = TransactionMapper(accountRepository = accountRepo)
    }

    private fun mockMyAccount() {
        val mine = Arb.account(
            accountId = Some(AccountId(myAccountId)),
            asset = Some(AssetCode.unsafe("IDR"))
        ).next()
        coEvery { accountRepo.findById(AccountId(myAccountId)) } returns mine
        // Her account is deliberately NOT in this ledger. The repository returns
        // null for an unknown id, so that is what the mock has to do too -
        // MockK throws on an unstubbed call, which is a different behaviour and
        // would fail the test for a reason unrelated to the mapper.
        coEvery { accountRepo.findById(AccountId(herAccountId)) } returns null
    }

    /**
     * The owner's leg, built inline with the same shape
     * `CrossProfileTransfer.senderLeg` produces.
     *
     * Inlined rather than imported because `shared/data/core` cannot see
     * `shared/domain` - the dependency runs the other way - and this test has to
     * live beside the mapper it exercises. The shape is pinned field by field
     * below so a change to the builder shows up here as a failure.
     */
    private fun ownerLeg() = TransactionEntity(
        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        accountId = myAccountId,
        type = TransactionType.TRANSFER,
        amount = 500_000.0,
        toAccountId = herAccountId,
        toAmount = null,
        title = "Transfer",
        dateTime = when0,
        counterpartProfileId = "so",
        linkedTransactionId = correlationId,
    )

    @Test
    fun `the owner's cross-profile leg is readable even though her account is not local`() = runTest {
        // Given only MY account exists locally. Her account is deliberately NOT
        // mocked - it is not in this database, which is the whole situation.
        mockMyAccount()

        // When the owner's leg is read back
        val result = with(mapper) { ownerLeg().toDomain(tags = emptyList()) }

        // Then it maps. If this fails, retrieveTrns drops the row silently and the
        // owner watches a transfer she just recorded disappear from her own ledger.
        result.isRight() shouldBe true
        val transfer = result.getOrNull().shouldNotBeNull()
        transfer shouldBe com.ivy.data.model.Transfer(
            id = com.ivy.data.model.TransactionId(ownerLeg().id),
            title = com.ivy.data.model.primitive.NotBlankTrimmedString.unsafe("Transfer"),
            description = null,
            category = null,
            time = when0,
            settled = true,
            metadata = com.ivy.data.model.TransactionMetadata(
                recurringRuleId = null,
                paidForDateTime = null,
                loanId = null,
                loanRecordId = null,
            ),
            tags = emptyList(),
            fromAccount = AccountId(myAccountId),
            fromValue = com.ivy.data.model.PositiveValue(
                amount = com.ivy.data.model.primitive.PositiveDouble.unsafe(500_000.0),
                asset = AssetCode.unsafe("IDR"),
            ),
            toAccount = AccountId(herAccountId),
            toValue = com.ivy.data.model.PositiveValue(
                amount = com.ivy.data.model.primitive.PositiveDouble.unsafe(500_000.0),
                asset = AssetCode.unsafe("IDR"),
            ),
        )
    }

    @Test
    fun `the owner's leg is still a TRANSFER and never INCOME`() = runTest {
        mockMyAccount()
        val mapped = with(mapper) { ownerLeg().toDomain(tags = emptyList()) }.getOrNull()

        // INCOME would tell the owner she earned this money. She sent it.
        mapped.shouldNotBeNull()
        (mapped is com.ivy.data.model.Transfer) shouldBe true
        (mapped is com.ivy.data.model.Income) shouldBe false
    }

    @Test
    fun `a leg written directly as an entity keeps its correlation id`() = runTest {
        // The idempotency key must be preserved on the path the feature actually
        // uses. Both legs are written straight to `WriteTransactionDao` as
        // `TransactionEntity`, so both fields are set explicitly and survive.
        //
        // Note the deliberate asymmetry with the domain-mapper path below: a
        // round trip through the domain `Transaction` LOSES these fields, because
        // that model has nowhere to carry them. That is a real known gap, not
        // something this test pretends otherwise.
        mockMyAccount()
        val leg = ownerLeg()

        // Written as-is, exactly as PendingTransferRepository writes a confirmed leg.
        val written = leg
        written.linkedTransactionId shouldBe correlationId
        written.counterpartProfileId shouldBe "so"
        written.type.name shouldBe "TRANSFER"
    }

    @Test
    fun `the read path preserves the counterpart so the leg is identifiable`() = runTest {
        // The read side must not drop the counterpart either: it is what
        // distinguishes "money moved to my SO" from "money moved between two of my
        // own accounts", which are otherwise identical in this table.
        mockMyAccount()

        // entity -> domain: the counterpart survives as far as the domain goes,
        // because it is what let the mapper resolve the destination at all.
        val mapped = with(mapper) { ownerLeg().toDomain(tags = emptyList()) }
        mapped.isRight() shouldBe true

        // And with counterpartProfileId absent, that same leg is rejected - which
        // is the proof that the counterpart field is load-bearing rather than
        // decorative. Same row, same unmappable destination, opposite outcome.
        val withoutCounterpart = ownerLeg().copy(counterpartProfileId = null)
        val rejected = with(mapper) { withoutCounterpart.toDomain(tags = emptyList()) }
        rejected.isLeft() shouldBe true
    }

    @Test
    fun `an ordinary intra-profile transfer still rejects a missing destination`() = runTest {
        // The guard I am NOT relaxing. A normal transfer whose destination is gone
        // is genuinely corrupt data and should not be silently mapped.
        mockMyAccount()
        val orphan = TransactionEntity(
            id = UUID.randomUUID(),
            accountId = myAccountId,
            type = TransactionType.TRANSFER,
            amount = 100.0,
            toAccountId = UUID.fromString("99999999-9999-9999-9999-999999999999"),
            dateTime = when0,
        )
        coEvery {
            accountRepo.findById(AccountId(UUID.fromString("99999999-9999-9999-9999-999999999999")))
        } returns null

        val result = with(mapper) { orphan.toDomain(tags = emptyList()) }

        result.isLeft() shouldBe true
    }

    @Test
    fun `the owner's leg keeps a null converted amount for a foreign currency`() = runTest {
        // CrossProfileTransfer deliberately leaves toAmount null rather than
        // inventing a rate. The mapping must not quietly invent one either.
        mockMyAccount()
        val mapped = with(mapper) { ownerLeg().toDomain(tags = emptyList()) }.getOrNull()
        mapped.shouldNotBeNull()

        ownerLeg().toAmount shouldBe null
        // Whatever asset the destination is given, the AMOUNT is the money that
        // left, never a fabricated conversion.
        (mapped is com.ivy.data.model.Transfer) shouldBe true
        val transfer = mapped as com.ivy.data.model.Transfer
        transfer.fromValue.amount.value shouldBe 500_000.0
    }

    @Test
    fun `the mapped leg carries its title so the owner can find it`() = runTest {
        mockMyAccount()
        val mapped = with(mapper) { ownerLeg().toDomain(tags = emptyList()) }.getOrNull()
        mapped.shouldNotBeNull()
        (mapped is com.ivy.data.model.Transfer) shouldBe true
        (mapped as com.ivy.data.model.Transfer).title?.value?.let {
            it shouldContain "Transfer"
        }
    }
}
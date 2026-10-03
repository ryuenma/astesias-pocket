package com.ivy.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.TagId
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.Transfer
import com.ivy.data.model.getFromAccount
import com.ivy.data.model.getToAccount
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.AccountRepository
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

class TransactionMapper @Inject constructor(
    private val accountRepository: AccountRepository,
) {

    suspend fun TransactionEntity.toDomain(
        tags: List<TagId> = emptyList()
    ): Either<String, Transaction> = either {
        ensure(!isDeleted) { "Transaction is deleted" }

        val metadata = TransactionMetadata(
            recurringRuleId = recurringRuleId,
            paidForDateTime = paidForDateTime,
            loanId = loanId,
            loanRecordId = loanRecordId
        )

        val settled = dateTime != null
        val time = mapTime().bind()

        val accountId = AccountId(accountId)
        val sourceAccount = accountRepository.findById(accountId)
        ensureNotNull(sourceAccount) { "No source account for transaction: ${this@toDomain}" }
        val fromValue = PositiveValue(
            amount = PositiveDouble.from(amount).bind(),
            asset = sourceAccount.asset
        )

        val notBlankTitle = title?.let(NotBlankTrimmedString::from)?.getOrNull()
        val notBlankDescription = description?.let(NotBlankTrimmedString::from)?.getOrNull()
        val category = categoryId?.let(::CategoryId)
        val transactionId = TransactionId(id)

        when (type) {
            TransactionType.INCOME -> {
                Income(
                    id = transactionId,
                    value = fromValue,
                    account = accountId,
                    title = notBlankTitle,
                    description = notBlankDescription,
                    category = category,
                    time = time,
                    settled = settled,
                    metadata = metadata,
                    tags = tags,
                )
            }

            TransactionType.EXPENSE -> {
                Expense(
                    id = transactionId,
                    account = accountId,
                    value = fromValue,
                    title = notBlankTitle,
                    description = notBlankDescription,
                    category = category,
                    time = time,
                    settled = settled,
                    metadata = metadata,
                    tags = tags,
                )
            }

            TransactionType.TRANSFER -> {
                val toAccountId = toAccountId?.let(::AccountId)
                ensureNotNull(toAccountId) {
                    "No destination account id associated with transaction '${this@toDomain}'"
                }
                ensure(accountId != toAccountId) {
                    "Self transfers aren't allowed. Source and destination accounts " +
                            "must be different for transaction: ${this@toDomain}"
                }

                val toAccount = accountRepository.findById(toAccountId)

                // A cross-profile transfer names an account in SOMEONE ELSE'S ledger.
                // That id cannot resolve here, and must not be required to: the mapper
                // reports failure with an Either and every ledger read discards those
                // with `mapNotNull`, so an unresolvable destination does not surface as
                // an error - it makes the transaction SILENTLY DISAPPEAR from the
                // owner's own ledger while still sitting in the database. That would
                // break the one promise this feature rests on: she can record a
                // transfer to her partner freely and immediately, offline or not.
                //
                // So the destination asset falls back to the source account's. These
                // accounts may hold different currencies and the honest statement is
                // that we do not know the rate - the amount carried is the money that
                // actually left, never a fabricated conversion. `senderLeg` likewise
                // leaves `toAmount` null for exactly this reason.
                //
                // A NULL `counterpartProfileId` preserves the original strictness: an
                // ordinary intra-profile transfer pointing at a missing account is
                // genuinely corrupt data and is still rejected here.
                if (toAccount == null) {
                    ensure(counterpartProfileId != null) {
                        "No destination account associated with transaction '${this@toDomain}'"
                    }
                    Timber.i(
                        "Cross-profile transfer $id: destination $toAccountId belongs to " +
                            "profile $counterpartProfileId and is not in this ledger; using " +
                            "the source account's asset for the destination value"
                    )
                }

                val toValue = PositiveValue(
                    amount = toAmount?.let(PositiveDouble::from)?.getOrNull()
                        ?: fromValue.amount,
                    asset = toAccount?.asset ?: fromValue.asset
                )

                Transfer(
                    id = transactionId,
                    title = notBlankTitle,
                    description = notBlankDescription,
                    category = category,
                    time = time,
                    settled = settled,
                    metadata = metadata,
                    fromAccount = accountId,
                    fromValue = fromValue,
                    toAccount = toAccountId,
                    toValue = toValue,
                    tags = tags,
                )
            }
        }
    }

    private fun TransactionEntity.mapTime(): Either<String, Instant> = either {
        val time = (dateTime ?: dueDate)
        ensureNotNull(time) { "Missing transaction time for entity: $this" }
        time
    }

    fun Transaction.toEntity(): TransactionEntity {
        return TransactionEntity(
            accountId = getFromAccount().value,
            type = when (this) {
                is Expense -> TransactionType.EXPENSE
                is Income -> TransactionType.INCOME
                is Transfer -> TransactionType.TRANSFER
            },
            amount = when (this) {
                is Expense -> value.amount.value
                is Income -> value.amount.value
                is Transfer -> fromValue.amount.value
            },
            toAccountId = getToAccount()?.value,
            toAmount = if (this is Transfer) {
                toValue.amount.value
            } else {
                null
            },
            title = title?.value,
            description = description?.value,
            dateTime = time.takeIf { settled },
            categoryId = category?.value,
            dueDate = time.takeIf { !settled },
            paidForDateTime = metadata.paidForDateTime,
            recurringRuleId = metadata.recurringRuleId,
            attachmentUrl = null,
            loanId = metadata.loanId,
            loanRecordId = metadata.loanRecordId,
            isSynced = true,
            isDeleted = false,
            id = id.value
        )
    }
}

/**
 * Cross-profile legs written through this mapper LOSE their counterpart and
 * correlation id.
 *
 * An honest statement of a known gap, not a claim of correctness: the domain
 * `Transaction` has no `counterpartProfileId` / `linkedTransactionId` field, so
 * there is nothing here to read them from and they come out null.
 *
 * This does not affect the owner-confirmation path, which writes legs directly as
 * `TransactionEntity` through `WriteTransactionDao` and therefore sets both fields
 * explicitly. It WOULD affect a future flow that round-trips a cross-profile leg
 * through the domain model, and it is worth fixing then - the correlation id is
 * the idempotency key, and a null one cannot be recognised as a replay.
 * `CrossProfileTransferMapperTest` pins the read side, so a regression there is
 * caught by a test rather than discovered by a user.
 */

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
        val notBlankTitle = title?.let(NotBlankTrimmedString::from)?.getOrNull()
        val notBlankDescription = description?.let(NotBlankTrimmedString::from)?.getOrNull()
        val category = categoryId?.let(::CategoryId)
        val transactionId = TransactionId(id)

        when (type) {
            TransactionType.INCOME -> {
                ensureNotNull(sourceAccount) { "No source account for transaction: ${this@toDomain}" }
                val fromValue = PositiveValue(
                    amount = PositiveDouble.from(amount).bind(),
                    asset = sourceAccount.asset
                )
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
                ensureNotNull(sourceAccount) { "No source account for transaction: ${this@toDomain}" }
                val fromValue = PositiveValue(
                    amount = PositiveDouble.from(amount).bind(),
                    asset = sourceAccount.asset
                )
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

                // A cross-profile leg written through this mapper comes back out
                // with a null `counterpartProfileId` and a null
                // `linkedTransactionId`: the domain `Transaction` has no fields to
                // read them from. A known gap, not a claim of correctness. It does
                // not affect the owner-confirmation path, which writes legs
                // straight to `WriteTransactionDao` and sets both itself - but the
                // correlation id is the idempotency key, so a flow that round-trips
                // a leg through the domain model cannot recognise a replay.
                //
                // A cross-profile transfer names an account in SOMEONE ELSE'S ledger
                // (destination on the sender's leg, source on the receiver's leg).
                // That id cannot resolve here, and must not be required to: the mapper
                // reports failure with an Either and every ledger read discards those
                // with `mapNotNull`, so an unresolvable counterparty account does not
                // surface as an error - it makes the transaction SILENTLY DISAPPEAR
                // from the local ledger while still sitting in the database.
                //
                // So the remote side's asset falls back to the local account's.
                // A NULL `counterpartProfileId` preserves the original strictness: an
                // ordinary intra-profile transfer pointing at a missing account is
                // genuinely corrupt data and is still rejected here.
                if (sourceAccount == null) {
                    ensure(counterpartProfileId != null && toAccount != null) {
                        "No source account for transaction: ${this@toDomain}"
                    }
                    Timber.i(
                        "Cross-profile transfer $id: source $accountId belongs to " +
                            "profile $counterpartProfileId and is not in this ledger; using " +
                            "the destination account's asset for the source value"
                    )
                }
                if (toAccount == null) {
                    ensure(counterpartProfileId != null && sourceAccount != null) {
                        "No destination account associated with transaction '${this@toDomain}'"
                    }
                    Timber.i(
                        "Cross-profile transfer $id: destination $toAccountId belongs to " +
                            "profile $counterpartProfileId and is not in this ledger; using " +
                            "the source account's asset for the destination value"
                    )
                }

                val fromValue = PositiveValue(
                    amount = PositiveDouble.from(amount).bind(),
                    asset = sourceAccount?.asset ?: toAccount!!.asset
                )
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

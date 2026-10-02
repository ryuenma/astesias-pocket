package com.ivy.domain.usecase.transfer

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import java.time.Instant
import java.util.UUID

/**
 * The two halves of a transfer between two people.
 *
 * Pure functions with no IO, so the shape of both legs is unit-testable without a
 * database. The IO around them lives in [CrossProfileTransferUseCase].
 *
 * Why two legs rather than one shared row: each profile owns its own database
 * file, and neither can hold the other's accounts. So the sender writes a row
 * that points at a destination account it does not own, and the receiver writes
 * the mirror image pointing back.
 */
object CrossProfileTransfer {

    /**
     * Builds the sender's leg: money leaving one of my accounts for one of hers.
     *
     * [toAmount] is left null rather than copied from [amount]. These accounts may
     * hold different currencies, and inventing a conversion rate here would be
     * worse than admitting we do not know it - the receiver's leg carries what
     * actually arrived.
     */
    fun senderLeg(
        legId: UUID,
        correlationId: UUID,
        fromAccountId: UUID,
        toAccountId: UUID,
        counterpartProfileId: String,
        amount: Double,
        dateTime: Instant,
        title: String? = null,
        description: String? = null,
    ): TransactionEntity = TransactionEntity(
        id = legId,
        accountId = fromAccountId,
        type = TransactionType.TRANSFER,
        amount = amount,
        toAccountId = toAccountId,
        toAmount = null,
        title = title,
        description = description,
        dateTime = dateTime,
        counterpartProfileId = counterpartProfileId,
        linkedTransactionId = correlationId,
    )

    /**
     * Builds the receiver's leg: the same money, arriving in one of her accounts
     * from one of mine.
     *
     * The two legs share only [correlationId]. Their account ids are mirrored, not
     * shared, so there is nothing else they could be joined on.
     *
     * [toAccountId] is null: from her side the money came from outside her wallet
     * rather than from another of her accounts, and there is no local account to
     * name.
     */
    fun receiverLeg(
        legId: UUID,
        correlationId: UUID,
        intoAccountId: UUID,
        fromAccountId: UUID,
        senderProfileId: String,
        amount: Double,
        dateTime: Instant,
        title: String? = null,
        description: String? = null,
    ): TransactionEntity = TransactionEntity(
        id = legId,
        accountId = intoAccountId,
        type = TransactionType.TRANSFER,
        amount = amount,
        toAccountId = null,
        toAmount = null,
        title = title,
        description = description,
        dateTime = dateTime,
        counterpartProfileId = senderProfileId,
        linkedTransactionId = correlationId,
    )
}

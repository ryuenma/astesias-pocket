package com.ivy.base.legacy

import androidx.compose.runtime.Immutable
import com.ivy.base.model.TransactionType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

typealias LegacyTransaction = Transaction

@Suppress("DataClassDefaultValues")
@Deprecated("Legacy data model. Will be deleted")
@Immutable
data class Transaction(
    // TODO: Remove default values & introduce Transaction#dummy() method
    val accountId: UUID,
    val type: TransactionType,
    val amount: BigDecimal,
    val toAccountId: UUID? = null,
    val toAmount: BigDecimal = amount,
    val title: String? = null,
    val description: String? = null,
    val dateTime: Instant? = null,
    val categoryId: UUID? = null,
    val dueDate: Instant? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val recurringRuleId: UUID? = null,

    /** to store the date for which the payment was made. */
    @Suppress("DataClassDefaultValues")
    val paidFor: Instant? = null,

    val attachmentUrl: String? = null,

    // This refers to the loan id that is linked with a transaction
    val loanId: UUID? = null,

    // This refers to the loan record id that is linked with a transaction
    val loanRecordId: UUID? = null,

    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,

    /**
     * Whose account the money went to, when that account is NOT in this ledger.
     *
     * A cross-profile transfer names a destination that lives in the other
     * person's database, so `toAccountId` is null BY DESIGN and
     * `TransactionMapper` refuses the leg unless this names who owns it. Without
     * it every cross-profile save threw, and because the save was wrapped in a
     * catch that swallowed the exception, the screen closed as though it had worked
     * and nothing was recorded.
     *
     * Null for an ordinary transfer, and that null is meaningful: it is what
     * preserves the original strictness for a transfer pointing at an account that
     * genuinely is missing.
     */
    @Suppress("DataClassDefaultValues")
    val counterpartProfileId: String? = null,

    @Suppress("DataClassDefaultValues")
    val tags: ImmutableList<LegacyTag> = persistentListOf(),

    val id: UUID = UUID.randomUUID()
) : TransactionHistoryItem

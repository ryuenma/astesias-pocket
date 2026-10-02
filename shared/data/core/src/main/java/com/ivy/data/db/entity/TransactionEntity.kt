package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerInstant
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import com.ivy.base.model.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(tableName = "transactions")
data class TransactionEntity(
    @SerialName("accountId")
    @Serializable(with = KSerializerUUID::class)
    val accountId: UUID,
    @SerialName("type")
    val type: TransactionType,
    @SerialName("amount")
    val amount: Double,
    @SerialName("toAccountId")
    @Serializable(with = KSerializerUUID::class)
    val toAccountId: UUID? = null,
    @SerialName("toAmount")
    val toAmount: Double? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("dateTime")
    @Serializable(with = KSerializerInstant::class)
    val dateTime: Instant? = null,
    @SerialName("categoryId")
    @Serializable(with = KSerializerUUID::class)
    val categoryId: UUID? = null,
    @SerialName("dueDate")
    @Serializable(with = KSerializerInstant::class)
    val dueDate: Instant? = null,
    @SerialName("recurringRuleId")
    @Serializable(with = KSerializerUUID::class)
    val recurringRuleId: UUID? = null,
    @SerialName("paidForDateTime")
    @Serializable(with = KSerializerInstant::class)
    val paidForDateTime: Instant? = null,
    @SerialName("attachmentUrl")
    val attachmentUrl: String? = null,
    /**
     * Profile that owns the OTHER leg of a cross-profile transfer.
     *
     * Null for every ordinary transaction, including Ivy's own intra-profile
     * transfers between two of the same person's accounts. Its presence is what
     * distinguishes "money moved between two of my accounts" from "money moved to
     * my SO", which otherwise look identical in this table.
     */
    @SerialName("counterpartProfileId")
    val counterpartProfileId: String? = null,

    /**
     * Correlation id shared by both legs of a cross-profile transfer.
     *
     * Cannot be derived from the accounts: the two legs point at different
     * accounts (mine -> hers, hers -> mine) so they share no account id. This id is
     * also the idempotency key - a device materialises the incoming leg only when
     * no transaction with this id is already in its database, so a replayed sync
     * cannot duplicate it.
     */
    @SerialName("linkedTransactionId")
    @Serializable(with = KSerializerUUID::class)
    val linkedTransactionId: UUID? = null,

    // This refers to the loan id that is linked with a transaction
    @SerialName("loanId")
    @Serializable(with = KSerializerUUID::class)
    val loanId: UUID? = null,
    // This refers to the loan record id that is linked with a transaction
    @SerialName("loanRecordId")
    @Serializable(with = KSerializerUUID::class)
    val loanRecordId: UUID? = null,
    @Deprecated("Obsolete field used for cloud sync. Can't be deleted because of backwards compatibility")
    @SerialName("isSynced")
    val isSynced: Boolean = false,
    @Deprecated("Obsolete field used for cloud sync. Can't be deleted because of backwards compatibility")
    @SerialName("isDeleted")
    val isDeleted: Boolean = false,

    @PrimaryKey
    @SerialName("id")
    @Serializable(with = KSerializerUUID::class)
    val id: UUID = UUID.randomUUID()
)

package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerInstant
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import com.ivy.base.model.TransactionType
import com.ivy.data.model.IntervalType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(tableName = "planned_payment_rules")
data class PlannedPaymentRuleEntity(
    @SerialName("startDate")
    @Serializable(with = KSerializerInstant::class)
    val startDate: Instant?,
    @SerialName("intervalN")
    val intervalN: Int?,
    @SerialName("intervalType")
    val intervalType: IntervalType?,
    @SerialName("oneTime")
    val oneTime: Boolean,
    @SerialName("type")
    val type: TransactionType,
    @SerialName("accountId")
    @Serializable(with = KSerializerUUID::class)
    val accountId: UUID,
    @SerialName("amount")
    val amount: Double = 0.0,
    @SerialName("categoryId")
    @Serializable(with = KSerializerUUID::class)
    val categoryId: UUID? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("description")
    val description: String? = null,

    /**
     * Day of the month to remind on, 1..31, or null for no monthly reminder.
     *
     * Nullable, and null is exactly what every existing row means today: this
     * rule has no reminder. See `Migration133to134_BillReminderDueDay` for why
     * this is not backfilled from [startDate] and why a rule could not express a
     * day before this column existed.
     *
     * Stored as a day-of-month rather than a date so it survives month-end: a
     * rule set to the 31st keeps meaning the 31st in February, where the clamped
     * occurrence is the 28th or 29th. See `BillReminderDates`.
     */
    @SerialName("dueDayOfMonth")
    val dueDayOfMonth: Int? = null,

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

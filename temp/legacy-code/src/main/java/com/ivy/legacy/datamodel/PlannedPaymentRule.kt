package com.ivy.legacy.datamodel

import androidx.compose.runtime.Immutable
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.model.IntervalType
import java.time.Instant
import java.util.UUID

@Deprecated("Legacy data model. Will be deleted")
@Immutable
data class PlannedPaymentRule(
    val startDate: Instant?,
    val intervalN: Int?,
    val intervalType: IntervalType?,
    val oneTime: Boolean,

    val type: TransactionType,
    val accountId: UUID,
    val amount: Double = 0.0,
    val categoryId: UUID? = null,
    val title: String? = null,
    val description: String? = null,

    /**
     * Day of the month to remind on, 1..31, or null for no monthly reminder.
     *
     * Nullable, and null is what every pre-existing rule means. See
     * `Migration133to134_BillReminderDueDay`.
     */
    val dueDayOfMonth: Int? = null,

    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,

    val id: UUID = UUID.randomUUID()
) {
    fun toEntity(): PlannedPaymentRuleEntity = PlannedPaymentRuleEntity(
        startDate = startDate,
        intervalN = intervalN,
        intervalType = intervalType,
        oneTime = oneTime,
        type = type,
        accountId = accountId,
        amount = amount,
        categoryId = categoryId,
        title = title,
        description = description,
        dueDayOfMonth = dueDayOfMonth,
        isSynced = isSynced,
        isDeleted = isDeleted,
        id = id
    )
}

package com.ivy.planned.list

import com.ivy.data.db.entity.PendingBillEntity
import com.ivy.data.model.Category
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.PlannedPaymentRule
import kotlinx.collections.immutable.ImmutableList
import java.time.ZoneId
import javax.annotation.concurrent.Immutable

@Immutable
data class PlannedPaymentsScreenState(
    val currency: String,
    val categories: ImmutableList<Category>,
    val accounts: ImmutableList<Account>,
    val oneTimePlannedPayment: ImmutableList<PlannedPaymentRule>,
    val oneTimeIncome: Double,
    val oneTimeExpenses: Double,
    val recurringPlannedPayment: ImmutableList<PlannedPaymentRule>,
    val recurringIncome: Double,
    val recurringExpenses: Double,
    val isOneTimePaymentsExpanded: Boolean,
    val isRecurringPaymentsExpanded: Boolean,

    /** Bills reminded about and not yet paid. Outside the ledger by design. */
    val pendingBills: ImmutableList<PendingBillEntity>,

    /** The owner's zone, so a due date is rendered on the calendar they use. */
    val zone: ZoneId,
)

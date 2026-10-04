package com.ivy.planned.list

sealed interface PlannedPaymentsScreenEvent {
    data class OnOneTimePaymentsExpanded(val isExpanded: Boolean) : PlannedPaymentsScreenEvent
    data class OnRecurringPaymentsExpanded(val isExpanded: Boolean) : PlannedPaymentsScreenEvent

    /**
     * The owner corrected a pending bill's amount.
     *
     * The original requirement: the amount is an estimate until he checks it, so it
     * has to be adjustable without editing the rule that produced it.
     */
    data class OnPendingBillAmountChanged(
        val occurrenceKey: String,
        val amount: Double,
    ) : PlannedPaymentsScreenEvent

    /** The owner discarded a pending bill without paying it. */
    data class OnPendingBillDismissed(val occurrenceKey: String) : PlannedPaymentsScreenEvent
}

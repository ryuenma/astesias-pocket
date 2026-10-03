package com.ivy.accounts

import androidx.compose.runtime.Immutable
import com.ivy.legacy.data.model.AccountData
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class AccountsState(
    val baseCurrency: String,
    val accountsData: ImmutableList<AccountData>,
    val totalBalanceWithExcluded: String,
    val totalBalanceWithExcludedText: String,
    val totalBalanceWithoutExcluded: String,
    val totalBalanceWithoutExcludedText: String,
    val reorderVisible: Boolean,
    val compactAccountsModeEnabled: Boolean,
    val hideTotalBalance: Boolean,

    /**
     * Balance held in accounts excluded from the main total - the owner's
     * savings, deposits and anything else parked out of the way.
     */
    val totalSavings: String,
    val totalSavingsText: String,
)

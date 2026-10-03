package com.ivy.wallet.domain.action.category

import com.ivy.data.category.CategoryTree
import com.ivy.data.model.Category
import com.ivy.data.model.Transaction
import com.ivy.data.temp.migration.getAccountId
import com.ivy.frp.action.FPAction
import com.ivy.frp.then
import com.ivy.legacy.datamodel.Account
import com.ivy.wallet.domain.action.transaction.CalcTrnsIncomeExpenseAct
import com.ivy.wallet.domain.action.transaction.LegacyCalcTrnsIncomeExpenseAct
import com.ivy.wallet.domain.pure.data.IncomeExpenseTransferPair
import javax.inject.Inject

class CategoryIncomeWithAccountFiltersAct @Inject constructor(
    private val calcTrnsIncomeExpenseAct: CalcTrnsIncomeExpenseAct
) : FPAction<CategoryIncomeWithAccountFiltersAct.Input, IncomeExpenseTransferPair>() {

    override suspend fun Input.compose(): suspend () -> IncomeExpenseTransferPair = suspend {
        val accountFilterSet = accountFilterList.map { it.id }.toHashSet()
        val tree = CategoryTree.of(allCategories)
        transactions.filter {
            tree.matches(it.category?.value, category)
        }.filter {
            if (accountFilterSet.isEmpty()) {
                true
            } else {
                accountFilterSet.contains(it.getAccountId())
            }
        }
    } then {
        CalcTrnsIncomeExpenseAct.Input(
            transactions = it,
            baseCurrency = baseCurrency,
            accounts = accountFilterList
        )
    } then calcTrnsIncomeExpenseAct

    data class Input(
        val transactions: List<Transaction>,
        val accountFilterList: List<Account>,
        val category: Category?,
        val baseCurrency: String,
        /**
         * Every category, parents and children alike.
         *
         * Needed for the rollup: a category's id alone says nothing about
         * which children it owns. Defaulted to empty so an existing caller
         * that has no list still behaves exactly as before (it just matches
         * the category itself).
         */
        val allCategories: List<Category> = emptyList(),
    )
}

@Deprecated("Uses legacy Transaction")
class LegacyCategoryIncomeWithAccountFiltersAct @Inject constructor(
    private val calcTrnsIncomeExpenseAct: LegacyCalcTrnsIncomeExpenseAct
) : FPAction<LegacyCategoryIncomeWithAccountFiltersAct.Input, IncomeExpenseTransferPair>() {

    override suspend fun Input.compose(): suspend () -> IncomeExpenseTransferPair = suspend {
        val accountFilterSet = accountFilterList.map { it.id }.toHashSet()
        val tree = CategoryTree.of(allCategories)
        transactions.filter {
            tree.matches(it.categoryId, category)
        }.filter {
            if (accountFilterSet.isEmpty()) {
                true
            } else {
                accountFilterSet.contains(it.accountId)
            }
        }
    } then {
        LegacyCalcTrnsIncomeExpenseAct.Input(
            transactions = it,
            baseCurrency = baseCurrency,
            accounts = accountFilterList
        )
    } then calcTrnsIncomeExpenseAct

    data class Input(
        val transactions: List<com.ivy.base.legacy.Transaction>,
        val accountFilterList: List<Account>,
        val category: Category?,
        val baseCurrency: String,
        /** See [CategoryIncomeWithAccountFiltersAct.Input.allCategories]. */
        val allCategories: List<Category> = emptyList(),
    )
}
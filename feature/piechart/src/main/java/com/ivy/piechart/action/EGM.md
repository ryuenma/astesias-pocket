# Pie Chart Aggregation (com.ivy.piechart.action)

## Overview
The functional action that turns raw transactions into the pie chart's slices: it
resolves usable accounts, sums income/expense per category in the base currency, and
optionally folds account-to-account transfers into a synthetic "account transfers"
category. Pure domain logic, injected as `PieChartAct`.

## Interface
- `class PieChartAct` — `FPAction<PieChartAct.Input, PieChartAct.Output>`; injects `AccountsAct`, `TrnsWithRangeAndAccFiltersAct`, `LegacyCalcTrnsIncomeExpenseAct`, `CategoryRepository`, `LegacyCategoryIncomeWithAccountFiltersAct`.
- `Input(baseCurrency: String, range: FromToTimeRange, type: TransactionType, accountIdFilterList: List<UUID>, treatTransferAsIncExp: Boolean = false, showAccountTransfersCategory: Boolean = treatTransferAsIncExp, existingTransactions: List<Transaction> = emptyList())`.
- `Output(totalAmount: Double, categoryAmounts: ImmutableList<CategoryAmount>)`.
- Private steps: `getUsableAccounts`, `calculateCategoryAmounts`, `calculateTotalAmount`, `addAccountTransfersCategory`.

## Invariants
- `showAccountTransfersCategory` defaults to `treatTransferAsIncExp`: if transfers count as income/expense they must also appear as their own category, otherwise the chart total would not match the sum of its slices.
- All amounts are converted to `Input.baseCurrency` before aggregation; the output never contains mixed currencies.
- `existingTransactions` short-circuits the query path when the caller already loaded the data — supplying it must not change the result, only avoid re-reading.
- `accountIdFilterList` is an **exclusion-aware** filter for the accounts underlying the chart: an empty list means "all accounts except the ones flagged excluded" (`filterExcluded`), and a non-empty list means exactly those account ids.
- The unspecified category (`category == null`) is a real bucket, so slices always sum to `totalAmount`.

## Side Effects
- Database reads through `TrnsWithRangeAndAccFiltersAct`, `CategoryRepository`, `AccountsAct` and the legacy income/expense use-cases.
- No writes, no network.

## Verification
- Run: `./gradlew :feature:piechart:assembleDebug` (this module has no `src/test` source set).

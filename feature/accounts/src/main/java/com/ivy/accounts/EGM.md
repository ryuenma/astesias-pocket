# Accounts Package (com.ivy.accounts)

## Overview
The accounts tab of the main screen: renders the list of wallet accounts with their
balances, the total-balance header, and the excluded-accounts (savings) breakdown.
Balances are computed through `CalcWalletBalanceAct`; the tab only formats and
presents what the legacy/domain use-cases return.

## Interface
- `AccountsEvent` — sealed event interface: `OnReorder(reorderedList: List<AccountData>)`, `OnReorderModalVisible(reorderVisible: Boolean)`.
- `AccountsState` — `@Immutable` state: `baseCurrency`, `accountsData: ImmutableList<AccountData>`, `totalBalanceWithExcluded(+Text)`, `totalBalanceWithoutExcluded(+Text)`, `reorderVisible`, `compactAccountsModeEnabled`, `hideTotalBalance`, `totalSavings(+Text)`.
- `AccountsViewModel` — `@HiltViewModel`, `ComposeViewModel<AccountsState, AccountsEvent>`; injects `Context`, `IvyWalletCtx`, `SharedPrefs`, `CalcWalletBalanceAct`, `BaseCurrencyAct`, `AccountDataAct`, `AccountRepository`, `DataObserver`.
- `BoxWithConstraintsScope.AccountsTab()` — tab entry point; hosts `AccountsViewModel` and dispatches `AccountsEvent`.
- `BoxWithConstraintsScope.UI(state: AccountsState, onEvent: (AccountsEvent) -> Unit)` — private stateless renderer.
- `AccountCard(...)`, `AccountHeader(...)` — private per-account composables inside `AccountsTab.kt`.
- `IncomeExpensesRow(income, expenses, currency, ...)` — reusable income/expenses split row (also used outside this package by the home header).

## Invariants
- `AccountsState` is `@Immutable` and built entirely in `AccountsViewModel.uiState()`; composables never mutate it.
- Reordering always re-persists the full list: each account is saved with `orderNum = index.toDouble()`, so list position is the single source of order.
- `compactAccountsModeEnabled` and `hideTotalBalance` come from `Features` (`compactAccountsMode`, `hideTotalBalance`), not from local UI state.
- `totalBalanceWithoutExcluded` excludes accounts flagged as excluded; `totalSavings` is the balance parked in those excluded accounts.
- `hideTotalBalance` gates monetary strings; the VM still computes them.

## Side Effects
- Writes to `AccountRepository` on every reorder (one `save` per account).
- Reads `SharedPrefs` (`TRANSFERS_AS_INCOME_EXPENSE`) while assembling state.
- Subscribes to `DataObserver.writeEvents` in `viewModelScope` to reload accounts after external writes.
- Navigates via `Navigation` (`nav.navigateTo`) on card/header clicks.

## Verification
- Run: `./gradlew :feature:accounts:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

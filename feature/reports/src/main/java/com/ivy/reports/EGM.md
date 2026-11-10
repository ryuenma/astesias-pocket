# Reports Package (com.ivy.reports)

## Overview
The reports screen: a filtered income/expense report over a time range, accounts,
categories, amounts, keywords and tags, with export to CSV/XLSX and the summary of
upcoming/overdue planned payments.

## Interface
- `ReportFilter` — filter payload: `id`, `trnTypes`, `period: TimePeriod?`, `accounts`, `categories`, `currency`, `minAmount`, `maxAmount`, `includeKeywords`, `excludeKeywords`, `includedTags`, `excludedTags`; `companion.emptyFilter(baseCurrency)`; `validate(): Boolean`.
- `ReportScreenEvent` — sealed **class** (not an interface): `OnFilter(filter?)`, `OnExport(context)`, `OnPayOrGet(transaction)`, `SkipTransaction`, `SkipTransactions`, `OnUpcomingExpanded`, `OnOverdueExpanded`, `OnFilterOverlayVisible`, `OnTagSearch(data)`, `OnTreatTransfersAsIncomeExpense`, plus the `…Legacy` variants for `com.ivy.base.legacy.Transaction`.
- `ReportScreenState` — `baseCurrency`, `balance`, `income`, `expenses`, `upcomingIncome`, `upcomingExpenses`, `overdueIncome`, `overdueExpenses`, `history`, `upcomingTransactions`, …
- `ReportViewModel` — `@HiltViewModel`, `ComposeViewModel<ReportScreenState, ReportScreenEvent>`; injects `PlannedPaymentsLogic`, `TransactionRepository`, `IvyWalletCtx`, `ExchangeAct`, `AccountsAct`, `CategoryRepository`, `TrnsWithDateDivsAct`, `CalcTrnsIncomeExpenseAct`, …
- `BoxWithConstraintsScope.ReportScreen(screen: ReportScreen)` — entry point; private `UI(state, onEventHandler)`.
- `BoxWithConstraintsScope.FilterOverlay(visible, baseCurrency, accounts, categories, allTags, filter, onClose, …)` — the filter editor overlay.

## Invariants
- `ReportFilter.validate()` requires non-empty `trnTypes`, a non-null `period`, non-empty `accounts` and non-empty `categories`; a half-filled filter is invalid and must not be applied.
- `minAmount > maxAmount` makes the filter invalid; the bounds are inclusive otherwise.
- `ReportFilter.id` is a fresh `UUID` per instance, so equal filters with different identities are distinct state values (Compose sees them as different).
- An empty filter defaults its `period` to `ivyContext.selectedPeriod` in the overlay; it does not silently keep a previous period.
- Keyword and tag lists are two separate mechanisms (free text vs `TagId`), and each has include and exclude sides.
- Legacy `com.ivy.base.legacy.Transaction` events exist alongside domain `Transaction` events and must be handled separately.

## Side Effects
- Reads transactions, accounts, categories, tags and planned payments from repositories within `viewModelScope`.
- Exports files: `OnExport(context)` writes CSV/XLSX to the location chosen through the `Context`.
- Mutates plans: `OnPayOrGet`/`SkipTransaction(s)` write transactions through the domain logic.
- Writes the `TRANSFERS_AS_INCOME_EXPENSE` preference when the toggle changes.

## Verification
- Run: `./gradlew :feature:reports:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

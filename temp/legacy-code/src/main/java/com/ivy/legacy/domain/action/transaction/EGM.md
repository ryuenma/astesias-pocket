# Transaction Actions

## Overview
The query and aggregate actions the transaction UI binds to: history, due/upcoming windows, by-id lookup, date-divider grouping and income/expense totals.

## Interface
- `AllTrnsAct`, `HistoryTrnsAct`, `DueTrnsAct`, `TrnByIdAct`, `TrnsWithRangeAndAccFiltersAct` - reads/filters.
- `TrnsWithDateDivsAct` and `HistoryWithDateDivsAct` - grouping into date-divider sections; both have `Legacy*` counterparts (`LegacyTrnsWithDateDivsAct`).
- `CalcTrnsIncomeExpenseAct` - totals per transaction list; `LegacyCalcTrnsIncomeExpenseAct` is the legacy-`Transaction` variant.

## Invariants
- Each aggregate exists twice (shared model and legacy model). The `Legacy*` classes are annotated `@Deprecated("Uses legacy Transaction")` and exist so legacy screens keep compiling; new call sites must use the non-legacy class.
- `CalcTrnsIncomeExpenseAct` converts into the base currency, so it needs exchange rates - it is the action that will fail silently if a rate is missing.
- The date-divider actions group but do not sort; callers must pass a list already in display order.
- Actions are `@Inject`-constructed and stateless between calls.

## Side Effects
- Read-only: repository reads plus exchange-rate reads. No writes in this package.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

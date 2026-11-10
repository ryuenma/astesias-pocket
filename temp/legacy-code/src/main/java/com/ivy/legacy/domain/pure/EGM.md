# Pure Domain Functions

## Overview
Functions declared `@Pure`/`@Total` in the FRP vocabulary (`com.ivy.frp.Annotations`): they must not read the clock, touch a repository, write a log or mutate anything. Time arrives as a parameter or through an injected `TimeProvider` whose value is passed in.

## Subdirectories & Boundaries
- [account](./account/EGM.md) - `filterExcluded` and `accountCurrency` account helpers.
- [data](./data/EGM.md) - Value types for pure calculations: `ClosedTimeRange`, `IncomeExpensePair`, `IncomeExpenseTransferPair`.
- [exchange](./exchange/EGM.md) - Currency exchange as pure functions: `ExchangeData`, `exchange`, `validExchangeRate`, `ExchangeTrns`.
- [transaction](./transaction/EGM.md) - Transaction folding, date dividers and value functions, with `Legacy*` mirrors for the legacy transaction type.
- [util](./util/EGM.md) - Arrow/`NonEmptyList` helpers: `nonEmptyListOfZeros`, `Option<BigDecimal>.orZero`, `mapIndexedNel`.

## Invariants
- Purity is a reviewed property, not compiler-enforced: the annotations are `SOURCE`-retained and only signal intent, so a `Date.now` call inside a `@Pure` function still compiles.
- Anything needing a rate, a clock or a repository takes it as an argument (`ExchangeEffect`, `TimeConverter`) instead of fetching it.
- Sub-packages are ordered by concern: `account`, `data`, `exchange`, `transaction`, `util`.

## Side Effects
- None by construction.

# Pure Transaction Functions

## Overview
The calculation core: folding a transaction list through one or more value functions, per-account and per-wallet value functions built on that fold, date dividers for the history list, and the income/expense/transfer filters. Each concept exists twice - once over the shared `com.ivy.data.model.Transaction` and once over the legacy model behind a `Legacy*` object.

## Interface
- `foldTransactions(...)` / `foldTransactionsSuspend(...)` and the internal tail-recursive `sumTransactionsInternal`/`sumTransactionsSuspendInternal`; `ValueFunction<A>` and `SuspendValueFunction<A>` type aliases.
- `sumTrns(transactions, valueFunction, argument): BigDecimal` - the single-sum convenience over the fold.
- `AccountValueFunctions` and `WalletValueFunctions` objects - the value functions for account-level and wallet-level sums.
- `transactionsWithDateDividers(...)` (`@Pure`) and `List<Transaction>.withDateDividers(...)` (`@Deprecated("Migrate to actions")`).
- `expenses`, `incomes`, `transfers`, `isUpcoming`, `isOverdue`, `trnCurrency` - all `@Pure` filters/selectors in `TrnFunctions.kt`.
- `LegacyFoldTransactions`, `LegacyTrnDateDividers`, `LegacyTrnFunctions`, `WalletValueFunctionsLegacy` - the mirror implementations over `com.ivy.base.legacy.Transaction`.

## Invariants
- The fold seeds with `nonEmptyListOfZeros(valueFunctions.size)` and returns one sum **per value function**, in the same order - callers index into the result by function position, so reordering the arguments reorders the output.
- Both folds are `tailrec`, so a transaction list large enough to matter will not blow the stack.
- `ValueFunction<A>` accumulates into `BigDecimal`; nothing here truncates to `Double`, and conversions back must not reintroduce floating point.
- The `Legacy*` mirrors exist solely because callers still hold the legacy `Transaction`; they take `com.ivy.base.legacy.Transaction` and must not be extended with new behaviour.
- `withDateDividers` (deprecated) and `transactionsWithDateDividers` (`@Pure`) are two entry points to the same grouping; new callers belong on the action-based path.

## Side Effects
- None: declared `@Pure`, no repository, clock or logger access.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

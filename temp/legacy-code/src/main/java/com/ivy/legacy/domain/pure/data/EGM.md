# Pure Data Value Types

## Overview
The small `@Immutable` value types the pure functions exchange: a closed time range and the income/expense pairs that aggregates are returned in.

## Interface
- `ClosedTimeRange(...)` - an explicit `from`/`to` range (as opposed to the open-ended legacy ranges).
- `IncomeExpensePair(...)` - `@Immutable` carrier for an income total and an expense total.
- `IncomeExpenseTransferPair(...)` - the same plus the transfer total, for wallets where transfers are tracked separately.

## Invariants
- `ClosedTimeRange` is the safe form: `FromToTimeRange.toCloseTimeRange()` in `data/model` can fail, `toCloseTimeRangeUnsafe()` does not - so a `ClosedTimeRange` reaching a pure function is already validated.
- The pairs are `@Immutable` and therefore safe as Compose state; they carry no timestamps of their own.
- Income, expense and transfer are separate fields rather than a signed total, so a transfer can never be double-counted as income or expense.

## Side Effects
- None.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

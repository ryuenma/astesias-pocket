# Pure Domain Utilities

## Overview
Arrow and `NonEmptyList` helpers shared by the pure transaction/exchange functions: initialising a list of sums, unwrapping an `Option<BigDecimal>` as a number, and mapping over a `NonEmptyList` while keeping the index.

## Interface
- `nonEmptyListOfZeros(n: Int): NonEmptyList<BigDecimal>` - the zero seed used by every fold that accumulates several sums at once.
- `Option<BigDecimal>.orZero(): BigDecimal` - unwraps an Arrow `Option` to `0`.
- `Double?.nextOrderNum(): Double` - ordering helper (`IvyDomainUtils.kt`): `this?.plus(1) ?: 0.0`.
- `mapIndexedNel` / `mapIndexedNelSuspend` - index-aware mapping over `NonEmptyList`, used by `FoldTransactions`.

## Invariants
- `mapIndexedNel` indexes the value functions and the running sums in lockstep; a mismatch in list lengths silently drops sums rather than throwing.
- `Double?.nextOrderNum()` maps `null` to `0.0`, so a missing order number sorts first, not last.
- Everything here is pure - no clock, no I/O, no injected providers.

## Side Effects
- None.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

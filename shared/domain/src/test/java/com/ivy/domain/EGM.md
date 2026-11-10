# com.ivy.domain (test) — domain test fixtures

## Overview

Property-test generators and helpers shared by the use-case tests, plus the per-subject test packages.
Everything here runs on the JVM against `shared/data/model-testing`'s arbiters.

## Interface

- `fun <T> List<T>.toNonEmptyList(): NonEmptyList<T>` — fails the test setup with a clear message when
  the list is empty.
- `fun NonEmptyList<PositiveValue>.sum(): PositiveDouble` — folds a non-empty list, saturating at
  `Double.MAX_VALUE` instead of overflowing.
- `Arb.Companion.nonEmptyTransfersOut(account, asset, min = 1, max = 100)` and
  `nonEmptyTransfersIn(...)` — kotest arbiters over the model-testing `transfer` generator.
- `Arb.Companion.transfersOut/transfersIn/...` (further generators in the same file).
- Subdirectories: `model/` → [./model/EGM.md](./model/EGM.md),
  `usecase/` → [./usecase/EGM.md](./usecase/EGM.md).

## Subdirectories & Boundaries

- [model/](./model/EGM.md) — Kotest assertion comparing StatSummary counts exactly and per-currency values approximately.
- [usecase/](./usecase/EGM.md) — Root of the domain use-case test packages (balance, billreminder, csv, stat, transfer, xlsx).

## Invariants

- `NonEmptyList.sum()` sets the running total to `Double.MAX_VALUE` on overflow and then
  `PositiveDouble.unsafe(sum)` — the result is a **valid but meaningless** positive number. A test that
  asserts an exact total must keep its generated values small enough not to overflow.
- `sum()` never returns a negative value, so it cannot express "the transfer exceeded the balance"; that
  case must be tested through the balance builders, which drop it.
- `min` is coerced with `coerceAtLeast(1)`: an arbiter for a non-empty list can never be asked for zero
  elements, so a caller passing `min = 0` silently gets 1.
- These helpers are test-only; nothing in `main/` may import them.

## Side Effects

- None: generators and pure folds.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

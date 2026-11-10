# com.ivy.data.temp.migration — transaction accessor shims

## Overview

Extension functions that read a sealed `Transaction` through the *entity-shaped* view the migration
needs: a single `BigDecimal` value, a single `UUID` account id and a `TransactionType` enum. They exist
because the legacy `Transaction` class and the new sealed model answer those questions differently.

## Interface

- `fun Transaction.getValue(): BigDecimal` — `Expense`/`Income` amount, or `Transfer.fromValue`.
- `fun Transaction.getAccountId(): UUID` — `Expense`/`Income` account, or `Transfer.fromAccount`.
- `fun Transaction.getTransactionType(): TransactionType`.
- `fun Transaction.settleNow(): Transaction` — copies with `settled = true` and `time = Instant.now()`.

## Invariants

- `getValue()` returns the **source** side of a transfer (`fromValue`), never `toValue`. For a
  cross-currency transfer that is not the same number, so a caller that means "what arrived" must not
  use this.
- `getValue()`/`getAccountId()` deliberately collapse the sealed hierarchy into one column each, which
  is why they are temporary: the information loss is exactly what the migration away from the entity
  shape must undo.
- `settleNow()` reads `Instant.now()` directly instead of `TimeProvider`, so it is **not deterministic**
  in tests. It is scaffolding, not a pattern to copy.
- Values are converted through `Double.toBigDecimal()`, which carries binary floating-point noise; do
  not use this path for money that must round-trip exactly.

## Side Effects

- `settleNow()` reads the system clock.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

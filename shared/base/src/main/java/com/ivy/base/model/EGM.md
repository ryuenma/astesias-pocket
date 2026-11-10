# com.ivy.base.model — shared enums

## Overview

Small enum types that cross module boundaries and are therefore kept out of `shared/data/model`
(which is the newer immutable domain model). Two of the four files declare
`package com.ivy.data.model` while living in the `com/ivy/base/model/` directory — Kotlin does not
require directory/package agreement, so this compiles, but it makes the package name in an
`import` misleading.

## Interface

- `enum class TransactionType { INCOME, EXPENSE, TRANSFER }` — `@Immutable`, `@Keep`, `@Serializable`, package `com.ivy.base.model`.
- `enum class LoanRecordType { INCREASE, DECREASE }` — `@Immutable`, `@Keep`, `@Serializable`, package `com.ivy.base.model`.
- `fun <T> LoanRecordType.processByType(decreaseAction: () -> T, increaseAction: () -> T): T`.
- `enum class IntervalType { DAY, WEEK, MONTH, YEAR }` — `@Keep`, **declares package `com.ivy.data.model`**.
- `enum class LoanType { BORROW, LEND }` — `@Keep`, **declares package `com.ivy.data.model`**.

## Invariants

- All four are `@Keep`: R8 must not strip or rename them because they are persisted by name
  (Room converters and kotlinx-serialization JSON).
- Enum constant names (`INCOME`, `EXPENSE`, `TRANSFER`, `DAY`, `WEEK`, ...) are persisted values.
  Reordering is safe; renaming is a data migration.
- `TransactionType` and `LoanRecordType` are `@Serializable`, so they can appear directly in
  `@Serializable` DTOs; `IntervalType` and `LoanType` are not.
- Do not add business logic here. `processByType` is the allowed exception: it is a total,
  side-effect-free `when` that the compiler keeps exhaustive.

## Side Effects

- None (pure data types).

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

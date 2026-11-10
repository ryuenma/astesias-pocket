# shared/data/model — immutable domain model

## Overview

Pure-Kotlin module (namespace `com.ivy.data.model`, plugin `ivy.feature`, **zero project
dependencies**) holding the vocabulary of the app: accounts, categories, tags, transactions,
monetary values, exchange rates, and the validated primitive value classes they are built from.
Validation is expressed at the type level with arrow-core's `Exact`/`Raise` DSL, so an
unconstructible value cannot reach a repository.

## Interface

- Entities: `Account`, `Category`, `Tag`, `TagAssociation`, `ExchangeRate`, `Transaction` (sealed),
  `Income`, `Expense`, `Transfer`, `TransactionMetadata`, `PositiveValue`, `Value`.
- Typed ids: `AccountId`, `CategoryId`, `TagId`, `TransactionId` — all `@JvmInline value class ... : UniqueId`.
- `enum class AccountType { Cash, Bank, Savings }` plus `val Account.type: AccountType?`.
- `interface Reorderable { val orderNum: Double }`.
- Extension algebra: `Transaction.getFromValue()`, `getFromAccount()`, `getToAccount()`.

## Subdirectories & Boundaries

- [src/](./src/EGM.md) — Source-set container for :shared:data:model (main + JVM unit tests).
- [src/main/kotlin/com/ivy/data/model/](./src/main/kotlin/com/ivy/data/model/EGM.md) — the entity package, which in turn documents `exact/`, `primitive/`, `sync/` and `util/`.

## Invariants

- **No Android dependencies.** This module has no `dependencies` block at all; adding androidx or
  Room imports here breaks the `shared/domain` → `shared/data/core` boundary and the JVM-only
  test story.
- Money is never a `Double` without a currency: `PositiveValue(amount: PositiveDouble, asset:
  AssetCode)` and `Value(amount: NonZeroDouble, asset: AssetCode)` are the only representations.
- `Transaction` is a **sealed interface with three disjoint shapes**. `getToAccount()` returns
  `null` for `Income`/`Expense` and the target for `Transfer`; treating income as a transfer
  inverts balance maths.
- `AccountType.Savings.includeInBalance == false` is the *entire* savings integration: Total
  Savings is derived as (all-accounts total − includeInBalance-only total). There is no
  savings-specific arithmetic anywhere, so hard-coding a savings total elsewhere double-counts.
- `Account.type` returns `null` for a custom icon on purpose — inferring a type would invent a
  classification the user never made. Callers must fall back to `Account.includeInBalance`.
- Subcategories are ordinary `Category` values with a `parentId`, not a distinct type; a
  subcategory id appears in exactly one parent's list.
- `Transaction.tags` carries a `TODO` acknowledging a known perf/complexity cost; removing it is
  a model migration, not a cleanup.

## Side Effects

- None: the module is pure data with no I/O, clock, or platform access.

## Verification

- Run: `./gradlew :shared:data:model:testDebugUnitTest`

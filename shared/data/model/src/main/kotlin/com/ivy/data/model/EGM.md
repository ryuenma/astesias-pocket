# com.ivy.data.model — entities and value algebra

## Overview

The concrete entity types and typed-id wrappers of the domain model, plus the small extension
algebra that treats `Income`/`Expense`/`Transfer` uniformly. Everything here is immutable
(`val`-only data classes) and Android-free.

## Interface

- `@JvmInline value class AccountId(value: UUID) : UniqueId`; `data class Account(id, name:
  NotBlankTrimmedString, asset: AssetCode, color: ColorInt, icon: IconAsset?, includeInBalance:
  Boolean, orderNum: Double) : Identifiable<AccountId>, Reorderable`.
- `enum class AccountType(icon: IconAsset, includeInBalance: Boolean) { Cash, Bank, Savings }`,
  `companion fun fromIcon(IconAsset?): AccountType?`, `val Account.type: AccountType?`.
- `@JvmInline value class CategoryId(...)`; `data class Category(id, name, color, icon, orderNum,
  parentId: CategoryId? = null)`.
- `data class Tag(id: TagId, name, description: String?, color, icon, orderNum, creationTimestamp: Instant)`;
  `data class TagAssociation(id: TagId, associatedId: AssociationId)`; `@JvmInline value class TagId(...)`.
- `sealed interface Transaction : Identifiable<TransactionId>` with `Income`, `Expense`, `Transfer`
  implementations and `data class TransactionMetadata(recurringRuleId, paidForDateTime, loanId, loanRecordId)`.
- `data class PositiveValue(amount: PositiveDouble, asset: AssetCode)`, `data class Value(amount: NonZeroDouble, asset: AssetCode)`.
- `data class ExchangeRate(baseCurrency, currency, rate: PositiveDouble, manualOverride: Boolean)`.
- `interface Reorderable { val orderNum: Double }`.
- `Transaction.getFromValue()`, `Transaction.getFromAccount()`, `Transaction.getToAccount()`.

## Subdirectories & Boundaries

- [exact/](./exact/EGM.md) — Exact<Value, ExactValue> validation interface with Raise-based spec, Either-returning from() and throwing unsafe(), plus ExactError.
- [primitive/](./primitive/EGM.md) — Validated primitive value classes (strings, doubles, ints, AssetCode, IconAsset, AssociationId, ColorInt) built on Exact with private constructors and nullable-free guarantees.
- [sync/](./sync/EGM.md) — Identity interfaces: UniqueId (UUID-backed) and Identifiable<ID>, the only place java.util.UUID appears in the model surface.
- [util/](./util/EGM.md) — HALF_EVEN monetary rounding for PositiveValue and Double.

## Invariants

- Every id is a `@JvmInline value class` over `UUID` implementing `UniqueId`; raw `UUID`s appear
  only inside `TransactionMetadata` (`recurringRuleId`, `loanId`, `loanRecordId`), which the
  `@Suppress("DataClassTypedIDs")` annotation acknowledges as debt.
- `Transaction` subtypes repeat the shared fields rather than inheriting a base data class —
  required for `data class` semantics; all three must keep identical field **names and order** for
  consumers that reflect or destructure them.
- `Transfer` has both `fromValue` and `toValue`; a cross-currency transfer is representable, so
  never assume `fromValue == toValue`.
- `Expense`/`Income` carry a `PositiveValue`, so a negative amount is unrepresentable; direction is
  encoded in the subtype, not the sign. This is the invariant that `TransactionMapper` and the
  balance builders rely on.
- `AccountType`'s icon is part of the type, so an existing account's stored icon resolves back to
  its type without a data migration.
- `Reorderable.orderNum` is a `Double` to allow efficient midpoint insertion between siblings.

## Side Effects

- None.

## Verification

- Run: `./gradlew :shared:data:model:testDebugUnitTest`

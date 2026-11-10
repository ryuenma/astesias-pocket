# com.ivy.data.repository.mapper — entity to domain mapping

## Overview

The boundary between persistence and the domain model. Each mapper takes a Room entity and returns a
domain object, validating with arrow's `Either`/`Raise` so an invalid row becomes a typed error rather
than an exception.

## Interface

- `class AccountMapper @Inject constructor(...)` — entity → `Account`.
- `class CategoryMapper @Inject constructor()` — entity → `Category`.
- `class ExchangeRateMapper @Inject constructor()` — entity/response → `ExchangeRate`.
- `class TagMapper @Inject constructor()` — entity → `Tag`.
- `class TransactionMapper @Inject constructor(AccountRepository)` —
  `suspend fun TransactionEntity.toDomain(tags: List<TagId> = emptyList()): Either<String, Transaction>`.

## Invariants

- `TransactionMapper.toDomain` is **`suspend`** and takes `AccountRepository`, because the entity only
  stores `accountId` while the domain `PositiveValue` needs the account's `AssetCode`. A mapper that
  returned a `Transaction` synchronously would have to invent an asset.
- `toDomain` rejects soft-deleted rows first (`ensure(!isDeleted) { "Transaction is deleted" }`) and
  returns `Either.Left`, so a caller must handle the deleted case explicitly.
- `settled` is derived as `dateTime != null` — there is no separate column. A transaction with a null
  `dateTime` is unsettled by definition.
- A missing source account is a hard error (`ensureNotNull(sourceAccount) { ... }`), not a defaulted
  asset; balances must never be computed against a guessed currency.
- `title`/`description` are coerced through `NotBlankTrimmedString.from(...).getOrNull()`, so a blank
  title becomes `null` rather than failing the whole mapping.
- Mapping is one-way (entity → domain). The reverse direction lives in the repositories'
  `toEntity`-style code so the domain model never has to know about Room.
- `DirectoryAccountEntity` is **not** mapped to a domain type: it is a sync cache, not a ledger entity.

## Side Effects

- `TransactionMapper.toDomain` performs a repository read per row (account lookup), so mapping a list
  is O(n) reads — acceptable because `AccountRepository.findById` is memoised.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

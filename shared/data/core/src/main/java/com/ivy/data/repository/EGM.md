# com.ivy.data.repository — repositories and the memo cache

## Overview

Repository classes are the data layer's public API to `shared/domain`: they translate entities to
domain models, memoise reads, and emit `DataWriteEvent`s on every write. `RepositoryMemo` is the
shared machinery that makes the memoisation and the event emission consistent.

## Interface

- `@Singleton class AccountRepository @Inject constructor(...)` — `findById(AccountId)`, `findAll()`,
  `findMaxOrderNum()`, `save(Account)`, `saveMany`, `deleteById(AccountId)`, `deleteAll()`.
- `@Singleton class CategoryRepository` — same shape for `CategoryId`.
- `@Singleton class TagRepository` — `findById`, `findByIds`, `findByAssociatedId`, `findAll`,
  `findByText`, `findByAllAssociatedIdForTagId`, `findByAllTagsForAssociations`,
  `associateTagToEntity(AssociationId, TagId)`, `removeTagAssociation`, `save`, `deleteById`, `deleteAll`.
- `class TransactionRepository` (11.5 KB) — ~20 `findAll…` query variants (per account, per type,
  between dates, by category, unspecified category, by recurring rule) plus `findById`, `findByIds`,
  `save`, `saveMany`, `deleteById`, `deleteAllByAccountId`.
- `class CurrencyRepository` — `getBaseCurrency(): AssetCode`, `setBaseBaseCurrency(AssetCode)`.
- `class ExchangeRatesRepository` — `fetchEurExchangeRates(): Either<String, List<ExchangeRate>>`,
  `findAll(): Flow<List<ExchangeRate>>`, `findAllManuallyOverridden()`, `save`, `saveManyRates`,
  `deleteAll`, `deleteByBaseCurrencyAndCurrency`.
- `class LegalRepository` — `isDisclaimerAccepted()`, `setDisclaimerAccepted(...)`.
- `class RepositoryMemoFactory @Inject constructor(DataObserver, DispatchersProvider)`
  - `fun <T, TID> createMemo(getDataWriteSaveEvent: (List<T>) -> DataWriteEvent, getDateWriteDeleteEvent: (DeleteOperation<TID>) -> DataWriteEvent): RepositoryMemo<T, TID>`
- `class RepositoryMemo<T, TID> internal constructor(...)` — `val items: Map<TID, T>`,
  `var findAllMemoized: Boolean` (private set), `findAll`, `findById`, `findByIds`, `save`, `saveMany`,
  `deleteById`, `deleteAll`.

## Subdirectories & Boundaries

- [fake/](./fake/EGM.md) — Test helper building a RepositoryMemoFactory with an unconfined dispatcher and a fresh DataObserver.
- [mapper/](./mapper/EGM.md) — Entity-to-domain mappers using Either-based validation; TransactionMapper is suspend and resolves the account asset, derives settled from dateTime and rejects deleted rows.

## Invariants

- **Every write goes through `RepositoryMemo`.** `memo.save/saveMany/deleteById/deleteAll` perform the
  DB write, update the cache, then post the corresponding `DataWriteEvent`, all inside
  `withContext(dispatchers.io)`. A repository method that touches a write DAO directly bypasses both
  the cache update and the event.
- `findAll` is **cached after the first call** (`findAllMemoized` latches to `true`) and the cached
  list is re-sorted by the caller-supplied `sortMemo` on every subsequent read. The first call wins;
  a sort change is applied to the cache, not re-queried.
- `findById` reads the cache first (`items[id]`) and only hits the DAO on a miss. A row written by
  another process, or a soft-delete performed outside the repository, is invisible until the memo is
  invalidated.
- `RepositoryMemo`'s constructor is `internal`: only `RepositoryMemoFactory` may build one, and every
  memo must be created with a **non-null** pair of event constructors. `createMemo`'s parameter is
  misspelled `getDateWriteDeleteEvent` — keep the name, it is part of the public factory signature.
- `CurrencyRepository.setBaseBaseCurrency` is the (misspelled) only way to change the ledger's base
  currency; it also drives exchange-rate invalidation.
- `RepositoryMemo` has no public invalidation entry point: correctness depends on every writer going
  through it.

## Side Effects

- All methods dispatch to `dispatchers.io`; writes emit `DataWriteEvent`s (see `DataObserver`).
- `ExchangeRatesRepository.findAll()` returns a Room `Flow`, so that read is *not* memoised — it
  re-emits on any table change.
- `ExchangeRatesRepository.fetchEurExchangeRates` performs a network call and returns `Either.Left` on
  failure.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

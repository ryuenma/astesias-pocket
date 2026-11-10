# com.ivy.data — data core root package

## Overview

Root package of the data module. Holds `DataObserver` — the single fan-out point for model writes —
and groups the twelve subsystems that make up the data layer.

## Interface

- `@Singleton class DataObserver @Inject constructor()`
  - `val writeEvents: Flow<DataWriteEvent>`
  - `suspend fun post(event: DataWriteEvent)`
- `sealed interface DataWriteEvent` with `data object AllDataChange`, and the `AccountChange`,
  `CategoryChange`, `TagChange` sub-hierarchies, each with `Save…`/`Delete…` variants.
- `sealed interface DeleteOperation<out Id : UniqueId>` — `data object All`,
  `data class Just<Id>(ids: List<Id>)`.

## Subdirectories & Boundaries

- [backup/](./backup/EGM.md) — Backup export/import: the AstesiaWalletCompleteData JSON document (all entity tables plus sharedPrefs), BackupDataUseCase, and ImportResult with errorMessage and duplicate-name warnings.
- [category/](./category/EGM.md) — CategoryTree: flattens categories into the two-level picker, promoting orphans, third levels and cycle members to roots while rollup walks the full subtree.
- [datasource/](./datasource/EGM.md) — LocalLegalDataSource: DataStore-backed legal disclaimer acceptance flag with tri-state (null = never asked) semantics.
- [datastore/](./datastore/EGM.md) — DataStore preferences instance (ivy_wallet_datastore_v1) and DatastoreKeys catalogue, including the feature_<key> convention and deprecated GitHub backup keys.
- [db/](./db/EGM.md) — Room database definition: IvyRoomDatabase at version 138 with 17 entities, 32 hand-written migrations plus one auto-migration (121->122), and RoomTypeConverters.
- [di/](./di/EGM.md) — Data-layer Hilt graph: RoomDbModule (per-profile database singleton plus one unscoped provider per DAO), DatastoreModule, KtorClientModule with BODY logging, and the remote data-source binding.
- [file/](./file/EGM.md) — SAF file I/O: UTF-16 string writes, raw byte writes for binary exports, BOM-sniffing reads, and the pure BackupEncoding detector.
- [profile/](./profile/EGM.md) — Per-profile isolation: allow-list-validated ProfileId used as a database filename, ProfileRegistry (synchronous, SharedPrefs-backed), ProfileSwitcher (process restart) and the read-only directory repository.
- [remote/](./remote/EGM.md) — Remote data-source contracts; currently EUR-based exchange-rate fetching returning Either<String, ExchangeRatesResponse>.
- [repository/](./repository/EGM.md) — Repositories for accounts, categories, tags, transactions, currency, exchange rates and legal state, plus RepositoryMemo/RepositoryMemoFactory which cache reads and emit DataWriteEvents after each write.
- [sync/](./sync/EGM.md) — PocketBase sync stack: SyncConfig (unconfigured by default, password never stored), DirectoryAuthApi/SyncToken expiry handling, directory and transfer API clients, DirectorySyncRepository, and the transfer outbox with idempotent draining.
- [temp/](./temp/EGM.md) — Holding area for temporary migration helpers; currently only the transaction accessor shims in migration/.

## Invariants

- `DataObserver` is a `MutableSharedFlow` with **no replay and no buffer** (default
  `MutableSharedFlow()`), so `post` suspends until every current subscriber has handled the event and
  events posted while nobody subscribes are lost. Do not treat `writeEvents` as a durable change log.
- `AllDataChange` implements `AccountChange` and `CategoryChange` **only** — it is *not* a
  `TagChange`. A subscriber that filters on `TagChange` never sees a wildcard invalidation.
- Emission happens inside `RepositoryMemo`'s write paths, after the DB write, on
  `dispatchers.io`. A repository that writes without going through `RepositoryMemo` produces no event
  and leaves every memoised reader stale.
- The `DataWriteEvent` variants carry full domain objects for saves (`List<Account>`) but ids for
  deletes (`DeleteOperation.Id`) — consumers must not assume a delete event can name the deleted value.

## Side Effects

- `post` suspends on backpressure and broadcasts to all current collectors; it performs no I/O.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

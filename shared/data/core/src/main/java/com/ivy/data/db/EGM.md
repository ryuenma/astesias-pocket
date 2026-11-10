# com.ivy.data.db — Room database

## Overview

The Room database definition and its type converters. `IvyRoomDatabase` is the single `@Singleton`
database handle for the active profile: 17 entities, version 138, `exportSchema = true`, and one
auto-migration (`121 → 122`, `DeleteSEMigration`) alongside 32 hand-written migrations.

## Interface

- `abstract class IvyRoomDatabase : RoomDatabase` — `@Database(entities = [...17...], autoMigrations =
  [AutoMigration(from = 121, to = 122, spec = IvyRoomDatabase.DeleteSEMigration::class)], version = 138,
  exportSchema = true)`, `@TypeConverters(RoomTypeConverters::class)`.
  - 17 `AccountDao`-style read DAO properties plus the 14 `Write…Dao` properties.
  - `companion object` with `create(applicationContext, profileId)`.
- `@Database` entity list: `AccountEntity`, `DirectoryAccountEntity`, `TransactionEntity`,
  `CategoryEntity`, `SettingsEntity`, `PlannedPaymentRuleEntity`, `UserEntity`, `ExchangeRateEntity`,
  `BudgetEntity`, `LoanEntity`, `LoanRecordEntity`, `TagEntity`, `TagAssociationEntity`,
  `PendingTransferEntity`, `PendingBillEntity`, `TransferOutboxEntity`, `TransferConfirmationEntity`.
- `class RoomTypeConverters` (declared in package **`com.ivy.domain.db`**, despite living here) —
  `LocalDateTime↔Long`, `UUID↔String`, `Theme`, `TransactionType`, `IntervalType`, `LoanType`,
  `Instant↔Long`, `LoanRecordType`.

## Subdirectories & Boundaries

- [dao/](./dao/EGM.md) — DAO layer split into read and write interfaces per table plus in-memory fakes, so RepositoryMemo can pair each write with its invalidation event.
- [entity/](./entity/EGM.md) — 17 Room entities that double as @Serializable backup/sync payloads, with @SerialName wire names, per-field UUID/Instant serializers and deprecated cloud-sync tombstones.
- [migration/](./migration/EGM.md) — 32 hand-written Room migrations for schema versions 105-138, split across packages com.ivy.domain.db.migration (<=122 plus 125to126) and com.ivy.data.db.migration (123+).

## Invariants

- **`version = 138` and `exportSchema = true` are a paired contract.** Bumping the version without a
  `Migration` (or `autoMigration`) fails `IvyRoomDatabaseMigrationTest` on device; changing a schema
  without bumping the version leaves installs crashing on open.
- **Every schema change must also commit the exported JSON** in `shared/data/core/schemas/`. That
  directory is loaded as `androidTest` assets, so a missing schema breaks the migration tests.
- The database is opened per profile: `create(applicationContext, profileId)` uses
  `profileId.dbFileName`. There is exactly one instance per process.
- `RoomTypeConverters` persists enums **by `name`** and dates as **epoch millis in UTC** (it uses the
  legacy `epochMilliToDateTime`/`toEpochMilli` helpers). Renaming an enum constant or changing the
  unit is a data migration, not a refactor.
- `LocalDateTime` is stored as UTC epoch millis through the *legacy* converters, while `Instant` is
  stored as epoch millis directly. Both columns look identical in SQLite; only the Kotlin type
  distinguishes them.
- The `121 → 122` step is handled by `AutoMigration` with `DeleteSEMigration`, not by a hand-written
  class — do not add `Migration121to122`.
- Room is exposed to consumers only as `implementation` (see the module contract), so neither this
  class nor its entities may appear in a signature that leaves the module.

## Side Effects

- Creates and opens `databases/<profileId>.db` plus `-wal`/`-shm` sidecar files.
- Executes pending `Migration`s on first open after an upgrade — a destructive or throwing migration
  bricks the app until reinstall.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`
- Migrations: `./gradlew :shared:data:core:connectedDebugAndroidTest`

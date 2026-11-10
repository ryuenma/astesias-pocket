# shared/data/core — Room, repositories, sync

## Overview

The data implementation module (namespace `com.ivy.data`). It owns the Room database, the DAO pair
(read/write) per table, mapper from entity to domain model, the repositories, profile isolation,
file/backup I/O and the PocketBase sync stack.

`build.gradle.kts`:

- plugins `ivy.feature`, `ivy.room`, `ivy.integration.testing`
- `api(projects.shared.base)`, `api(projects.shared.data.model)` — **`api`, not `implementation`**,
  with an in-file comment explaining why: `ProfileRegistry` and SharedPrefs-bearing constructors are
  part of this module's public API and consumers get them injected via Hilt; under `implementation`
  the type is absent from a consumer's compile classpath and Hilt's KSP step fails with the opaque
  `error.NonExistentClass`.
- `api(libs.datastore)`, `implementation(libs.bundles.ktor)`
- `testImplementation(projects.shared.data.modelTesting)`, `androidTestImplementation(...integration testing)`

## Interface

- `DataObserver` (in `com.ivy.data`) — write-event fan-out.
- `db/` → [./src/main/java/com/ivy/data/db/EGM.md](./src/main/java/com/ivy/data/db/EGM.md)
- `repository/` → [./src/main/java/com/ivy/data/repository/EGM.md](./src/main/java/com/ivy/data/repository/EGM.md)
- `profile/` → [./src/main/java/com/ivy/data/profile/EGM.md](./src/main/java/com/ivy/data/profile/EGM.md)
- `sync/` → [./src/main/java/com/ivy/data/sync/EGM.md](./src/main/java/com/ivy/data/sync/EGM.md)
- `file/` → [./src/main/java/com/ivy/data/file/EGM.md](./src/main/java/com/ivy/data/file/EGM.md)
- `backup/` → [./src/main/java/com/ivy/data/backup/EGM.md](./src/main/java/com/ivy/data/backup/EGM.md)
- `category/` → [./src/main/java/com/ivy/data/category/EGM.md](./src/main/java/com/ivy/data/category/EGM.md)
- `datastore/` → [./src/main/java/com/ivy/data/datastore/EGM.md](./src/main/java/com/ivy/data/datastore/EGM.md)
- `datasource/` → [./src/main/java/com/ivy/data/datasource/EGM.md](./src/main/java/com/ivy/data/datasource/EGM.md)
- `remote/` → [./src/main/java/com/ivy/data/remote/EGM.md](./src/main/java/com/ivy/data/remote/EGM.md)
- `di/` → [./src/main/java/com/ivy/data/di/EGM.md](./src/main/java/com/ivy/data/di/EGM.md)
- `temp/` → [./src/main/java/com/ivy/data/temp/EGM.md](./src/main/java/com/ivy/data/temp/EGM.md)
- `src/` → [./src/EGM.md](./src/EGM.md)
- `schemas/` — 37 exported Room schema JSONs (`com.ivy.data.db.IvyRoomDatabase/101.json` …), the
  artifact `exportSchema = true` produces. Data, not code; no contract file.

## Subdirectories & Boundaries

- [src/](./src/EGM.md) — Source-set container for :shared:data:core: main, test and androidTest (instrumented migration/import tests).

## Invariants

- **Room is `implementation`, not `api`.** `ivy.room` adds `implementation(libs.bundles.room)` plus
  `ksp(libs.room.compiler)`, so Room types must not appear in any public signature that consumers
  see. `DirectoryRepository` documents the consequence: it deliberately does *not* take
  `IvyRoomDatabase` "just to call `withTransaction`", because that would drag `RoomDatabase` into
  every consumer's injected signature where Hilt's KSP step cannot resolve it.
- **Dependency direction: `shared/domain` may see `shared/data/core`; `shared/data/core` must never
  see `shared/domain`.** `domain/build.gradle.kts` declares `implementation(projects.shared.data.core)`;
  this module's dependency list contains only `base` and `data/model`. Adding a domain dependency
  here creates a cycle, and the correct direction for orchestration is a use case in `domain/`.
- `api` vs `implementation` on `base`/`model` is load-bearing for Hilt codegen (see above); do not
  "tidy" it to `implementation`.
- One database file per profile, so isolation is a filesystem property. Nothing here may hold a
  long-lived reference to a database that a profile switch could invalidate — that is why
  `ProfileSwitcher` restarts the process.

## Side Effects

- Opens/creates a SQLite database file under `databases/<profileId>.db`.
- Reads and writes the `ivy_wallet_prefs` `SharedPreferences` (active profile, sync config, legacy
  settings).
- Reads and writes an AndroidX `DataStore` preferences file (`ivy_wallet_datastore_v1`).
- Performs network calls to PocketBase for sync and to `currency-api.pages.dev` for exchange rates.
- Reads/writes user files through `ContentResolver` URIs (CSV/XLSX/JSON/ZIP backups).

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`
- Instrumented (migrations, real import): `./gradlew :shared:data:core:connectedDebugAndroidTest`

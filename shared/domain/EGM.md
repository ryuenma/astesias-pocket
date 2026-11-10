# shared/domain — use cases and feature flags

## Overview

Android library module (namespace `com.ivy.domain`) holding the app's business operations. It depends
on `implementation(projects.shared.base)` and `implementation(projects.shared.data.core)`, plus
`datastore`, the Ktor and opencsv bundles, and applies `ivy.feature`, `ivy.integration.testing` and
`ivy.room`.

This is the **only layer allowed to orchestrate**: it reads repositories from `data/core` and exposes
named operations to `ui` and `app`. `data/core` never sees this module, so nothing here may be
referenced from the data layer.

## Interface

- `AppStarter`, `RootScreen` (in `com.ivy.domain`) — the ports `app/` implements.
- `di/` → [./src/main/java/com/ivy/domain/di/EGM.md](./src/main/java/com/ivy/domain/di/EGM.md)
- `features/` → [./src/main/java/com/ivy/domain/features/EGM.md](./src/main/java/com/ivy/domain/features/EGM.md)
- `model/` → [./src/main/java/com/ivy/domain/model/EGM.md](./src/main/java/com/ivy/domain/model/EGM.md)
- `usecase/` → [./src/main/java/com/ivy/domain/usecase/EGM.md](./src/main/java/com/ivy/domain/usecase/EGM.md)
- `src/` → [./src/EGM.md](./src/EGM.md)
- `schemas/` — the Room schema directory declared by the `ivy.room` plugin
  (`room { schemaDirectory("$projectDir/schemas") }`). It is currently **empty**: this module declares
  no `@Database`, and the plugin is applied only so the convention is uniform. Not documented further.
- `build/` — Gradle output, generated.

## Subdirectories & Boundaries

- [src/](./src/EGM.md) — Source-set container for :shared:domain (main, test, thin androidTest).

## Invariants

- **`shared/domain` may see `shared/data/core`; `shared/data/core` may not see this module.** The
  Gradle edge exists only on this side. Adding a `domain` import into `data/core` or an
  `implementation(projects.shared.domain)` there is a build cycle.
- **Room is `implementation` in `data/core`**, so no type here may expose Room in a public signature —
  and this module must not re-export `RoomDatabase`-typed members either, even though it applies
  `ivy.room` for the schema convention.
- Orchestration belongs here, not in `data/core` and not in `ui`: a multi-repository operation is a use
  case in this module.
- `AppStarter` and `RootScreen` are **interfaces implemented by `app/`** — that inversion is what keeps
  this module free of an app dependency. Adding an `app` import here breaks the layering.
- `ivy.integration.testing` is applied, so `androidTest` exists and uses `MockK` for Android types.

## Side Effects

- Use cases read/write the database (through repositories), the DataStore (feature flags, device id),
  files (CSV/XLSX export) and the network (exchange-rate sync, PocketBase transfers).
- `usecase/android/DeviceIdUseCase` persists a generated id in DataStore.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`
- Instrumented: `./gradlew :shared:domain:connectedDebugAndroidTest`

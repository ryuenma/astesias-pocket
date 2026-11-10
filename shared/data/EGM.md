# shared/data — data layer modules

## Overview

Groups the data layer: the immutable domain model (`model`), the Room/Ktor implementation
(`core`), and a test-fixture module (`model-testing`). Dependency direction inside this directory
is strictly `core → model`; `model` never depends on `core`, and `model-testing` depends only on
`model`.

## Interface

- `core/` → [./core/EGM.md](./core/EGM.md) — Room database, DAOs, repositories, sync, profiles.
- `model/` — [./model/EGM.md](./model/EGM.md) — pure Kotlin value classes and domain entities.
- `model-testing/` — [./model-testing/EGM.md](./model-testing/EGM.md) — arbiters/assertions shared by unit tests.

## Subdirectories & Boundaries

- [core/](./core/EGM.md) — Data implementation module (com.ivy.data): Room database with 138 schema versions, read/write DAOs, repositories with a memo cache, per-profile isolation, backup/CSV/XLSX I/O and PocketBase sync.
- [model/](./model/EGM.md) — Pure-Kotlin domain model (com.ivy.data.model): Account/Category/Tag/Transaction entities, typed ids, AccountType with savings semantics, monetary values and validated primitives.
- [model-testing/](./model-testing/EGM.md) — Test-fixture module publishing kotest arbiters/assertions for the domain model; consumed only via testImplementation.

## Invariants

- `shared/data` itself declares no build file and no source: it is a Gradle directory grouping
  only, inferred by Gradle's project-path mapping (`shared:data:core`). Adding source here would
  create an unnamed module.
- `model` must stay free of Android/Room imports so it can be consumed by `shared/domain` and by
  unit tests on the JVM.

## Side Effects

- None at this level; effects belong to `core/`.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

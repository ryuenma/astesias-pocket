# test — unit and fixture source set

## Overview

Test source set of `:shared:domain`. Holds JVM unit tests plus the property-test generators and
assertions that the use-case tests are built on.

## Interface

- `java/` → [./java/EGM.md](./java/EGM.md)
- `snapshots/` — recorded snapshot data used by the module's tests. Data directory; no contract file.

## Subdirectories & Boundaries

- [java/](./java/EGM.md) — Test Kotlin source root of :shared/domain.

## Invariants

- Tests here must run on the JVM: no `Context`, no Room. Anything needing either belongs in
  `androidTest/`.

## Side Effects

- May write files under `snapshots/` when a snapshot is recorded.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

# test — JVM unit tests

## Overview

Unit-test source set of `:shared:data:core`. Holds Kotlin tests plus fixtures, resources and Paparazzi-
style snapshots used by the DAO/repository layer tests.

## Interface

- `java/` → [./java/EGM.md](./java/EGM.md)
- `resources/` — test resources (including Room schema assets copied in by the `ivy.room` plugin).
- `snapshots/` — recorded snapshot output for the module's tests.
- Both `resources/` and `snapshots/` are data directories outside this partition; no contract files.

## Subdirectories & Boundaries

- [java/](./java/EGM.md) — Test Kotlin source root of :shared:data:core.

## Invariants

- These tests run on the JVM against the `Fake…Dao` implementations, not Room. Anything that needs a
  real SQLite engine belongs in `androidTest/`, not here.

## Side Effects

- Writes snapshot/log files under `snapshots/` when a snapshot is recorded.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

# src — shared/domain source sets

## Overview

Source-set container for `:shared:domain`: `main/`, `test/` (JVM unit tests) and `androidTest/`
(instrumented, MockK-based).

## Interface

- `main/` → [./main/EGM.md](./main/EGM.md)
- `test/` → [./test/EGM.md](./test/EGM.md)
- `androidTest/java/com/ivy/domain/usecase/SyncExchangeRatesUseCaseTest.kt` — instrumented test for the
  exchange-rate sync. Not owned by this partition; no contract file.

## Subdirectories & Boundaries

- [main/](./main/EGM.md) — Production source set of :shared:domain.
- [test/](./test/EGM.md) — Test source set of :shared/domain, holding property-test generators, assertions and use-case tests.

## Invariants

- `androidTest` here is thin (one test): MockK's Android mocking is applied
  (`androidTestImplementation(libs.mockk.android)`), which is why those tests cannot run on the JVM.

## Side Effects

- None.

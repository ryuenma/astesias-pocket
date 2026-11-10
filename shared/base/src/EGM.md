# src — shared/base source sets

## Overview

Gradle source-set container for `:shared:base`. `main/` holds production Kotlin; `test/` holds
JVM unit tests (not documented in this bootstrap — test-only).

## Interface

- `main/` → [main/EGM.md](./main/EGM.md)
- `test/java/com/ivy/base/time/impl/` — `DeviceTimeProviderTest`, `StandardTimeConverterTest`,
  `StandardTimeConverterPropertyTest`. Test-only; no contract file.

## Subdirectories & Boundaries

- [main/](./main/EGM.md) — Production source set of :shared:base; single Kotlin root java/.

## Invariants

- There is no `androidTest/` source set here: `shared/base` is pure JVM-testable and declares no
  instrumentation tests.

## Side Effects

- None; a source-set directory holds no executable code by itself.

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

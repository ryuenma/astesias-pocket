# src — shared/data/model source sets

## Overview

Source-set container for `:shared:data:model`. Production code lives in `main/`; `test/` holds
JVM unit tests for the primitive value classes (test-only, not documented).

## Interface

- `main/` → [./main/EGM.md](./main/EGM.md)
- `test/java/com/ivy/data/model/primitive/` — `AssetCodeTest`, `IconIdTest`, `NonNegativeDoubleTest`,
  `NonNegativeIntTest`, `NonZeroDoubleTest`, `NotBlankTrimmedStringTest`, `PositiveDoubleTest`,
  `PositiveIntTest`. Test-only; no contract file.

## Subdirectories & Boundaries

- [main/](./main/EGM.md) — Production source set of :shared:data:model; single Kotlin root kotlin/.

## Invariants

- No `androidTest/`: the module is Android-free by design, which is why its tests are plain JVM tests.

## Side Effects

- None.

# com.ivy — organization package root (data core)

## Overview

`com.ivy` package root of `:shared:data:core`; contains only `data/`.

## Interface

- `data/` → [./data/EGM.md](./data/EGM.md)

## Subdirectories & Boundaries

- [data/](./data/EGM.md) — Root package of the data module: DataObserver write-event fan-out (MutableSharedFlow, no replay) with the DataWriteEvent/DeleteOperation hierarchies, grouping twelve data subsystems.

## Invariants

- No files at this level. Note that this module's sources declare **two** packages:
  `com.ivy.data.*` for new code and `com.ivy.domain.db*` for code that predates the module split
  (see `db/`). The Gradle module and the Kotlin package are not in one-to-one correspondence.

## Side Effects

- None.

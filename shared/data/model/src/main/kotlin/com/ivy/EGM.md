# com.ivy — organization package root

## Overview

`com.ivy` package root for `:shared:data:model`; contains only `data/`.

## Interface

- `data/` → [./data/EGM.md](./data/EGM.md)

## Subdirectories & Boundaries

- [data/](./data/EGM.md) — Package root for com.ivy.data.model in the model module; only model/ below.

## Invariants

- No source files at this level. Note that `com.ivy.data.model` (this module) and
  `com.ivy.data` (in `shared/data/core`) are distinct Gradle modules sharing a package prefix;
  `shared/data/core` also declares `namespace = "com.ivy.data"`.

## Side Effects

- None.

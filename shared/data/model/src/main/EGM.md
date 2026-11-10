# main — production source set

## Overview

Production source set of `:shared:data:model`. Contains a single Kotlin root named `kotlin/`
(unlike `shared/base`, which uses `java/`).

## Interface

- `kotlin/` → [./kotlin/EGM.md](./kotlin/EGM.md)

## Subdirectories & Boundaries

- [kotlin/](./kotlin/EGM.md) — Kotlin source root of :shared:data:model.

## Invariants

- No manifest, no `res/`: this is a pure Kotlin module with no Android surface.

## Side Effects

- None.

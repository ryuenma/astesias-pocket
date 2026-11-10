# main — production source set

## Overview

`main` source set of `:shared:base`. Contains only the Kotlin source root `java/` — the module
uses `java/` for Kotlin files, not `kotlin/`.

## Interface

- `java/` → [java/EGM.md](./java/EGM.md)

## Subdirectories & Boundaries

- [java/](./java/EGM.md) — Kotlin source root of :shared:base (java/ directory name, .kt files only).

## Invariants

- No `AndroidManifest.xml` and no `res/`: the module contributes no manifest entries or
  resources; Hilt aggregation is driven by the `ivy.feature`/`ivy.hilt` convention plugins.

## Side Effects

- None.

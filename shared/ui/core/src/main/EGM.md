# main — production source set

## Overview

Production source set of `:shared:ui:core`. Unusually for this repo it contains **both** a Kotlin
root (`java/`) and an Android resource root (`res/`), because the module owns drawables used by its
components.

## Interface

- `java/` → [./java/EGM.md](./java/EGM.md)
- `res/` — 700+ drawables/values used by the design system (e.g. `drawable/github_logo.xml`, brand
  and logo assets). Not owned by this partition; no contract file.

## Subdirectories & Boundaries

- [java/](./java/EGM.md) — Kotlin source root of :shared:ui:core.

## Invariants

- `com.ivy.ui.R` is generated from `res/`, so `OpenSourceCard` can reference
  `R.drawable.github_logo` and `R.string.ivy_wallet_open_source`. Deleting a drawable referenced by
  a component breaks compilation of this module, not of the consumer.
- Resources here are namespaced to `com.ivy.ui`; a consumer must import `com.ivy.ui.R`, not the
  app's `R`.

## Side Effects

- None.

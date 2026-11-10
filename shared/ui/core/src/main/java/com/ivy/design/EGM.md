# com.ivy.design — design entry point

## Overview

Root of the design-system tree. Currently only `system/`, which holds the Material 3 theme and the
palette it is built from.

## Interface

- `system/` → [./system/EGM.md](./system/EGM.md)

## Subdirectories & Boundaries

- [system/](./system/EGM.md) — IvyMaterial3Theme wrapper mapping the Ivy palette to explicit light/dark Material 3 ColorSchemes, with AMOLED true-black support.

## Invariants

- This package must contain no composables of its own: theming lives in `system/`, components in
  `com.ivy.ui.component`. Mixing the two is how design tokens leak into feature code.

## Side Effects

- None.

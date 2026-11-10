# com.ivy — organization package root (navigation)

## Overview

`com.ivy` package root for `:shared:ui:navigation`; contains only `navigation/`.

## Interface

- `navigation/` → [./navigation/EGM.md](./navigation/EGM.md)

## Subdirectories & Boundaries

- [navigation/](./navigation/EGM.md) — Implementation of the navigation model: Screen marker, the Screens data-object/data-class catalogue, the Stack-based Navigation singleton, NavigationRoot + screenScopedViewModel, and IvyPreview.

## Invariants

- No files at this level. `com.ivy.navigation` is a distinct module from `com.ivy.ui`, even though
  both live under `shared/ui`.

## Side Effects

- None.

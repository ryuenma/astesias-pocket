# com.ivy — organization package root (UI)

## Overview

`com.ivy` package root for `:shared:ui:core`. It contains **two** sibling trees with different
owners: `design/` (the design system) and `ui/` (shared UI logic), plus the generated `R` class in
`com.ivy.ui`.

## Interface

- `design/` → [./design/EGM.md](./design/EGM.md)
- `ui/` → [./ui/EGM.md](./ui/EGM.md)

## Subdirectories & Boundaries

- [design/](./design/EGM.md) — Design-system entry package; contains only system/.
- [ui/](./ui/EGM.md) — Shared UI logic: ComposeViewModel base class, FormatMoneyUseCase (Indonesian rb/jt/M/T shortening), and rememberScrollPositionListState with a process-static scroll cache.

## Invariants

- `com.ivy.ui` is both the module's Android namespace (generated `R`) and a real source package.
  Emptying the source package would change the `R` package, breaking every `com.ivy.ui.R` import.
- No files live directly in this directory.

## Side Effects

- None.

# com.ivy — organization package root

## Overview

`com.ivy` package root shared by every module in the repository (`com.ivy.base`, `com.ivy.data`,
`com.ivy.domain`, `com.ivy.ui`, `com.ivy.navigation`). In this module it contains only `base/`.

## Interface

- `base/` → [base/EGM.md](./base/EGM.md)

## Subdirectories & Boundaries

- [base/](./base/EGM.md) — Root package of :shared:base: Toaster (main-thread toast helper) and test dispatcher fixtures, grouping di/, kotlinxserilzation/, legacy/, model/, resource/, threading/, time/.

## Invariants

- Empty of source files; it exists so every module's packages sit under one namespace root.

## Side Effects

- None.

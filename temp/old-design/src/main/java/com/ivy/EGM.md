# Design Module Packages

## Overview
Top-level package chain of `:temp:old-design`; only `design` is present, so the module's whole surface is `com.ivy.design.*`.

## Subdirectories & Boundaries
- [design](./design/EGM.md) - Package root `com.ivy.design`: the `IvyContext` base type and the colour-picker palettes.

## Invariants
- Unlike `:temp:legacy-code`, this module's directory and package do match: `src/main/java/com/ivy/design/...` holds `com.ivy.design....`.

## Side Effects
- None: grouping level.

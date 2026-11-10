# src — shared/ui/navigation source sets

## Overview

Source-set container for `:shared:ui:navigation`. Only `main/` exists — the module ships no tests of
its own.

## Interface

- `main/` → [./main/EGM.md](./main/EGM.md)

## Subdirectories & Boundaries

- [main/](./main/EGM.md) — Production source set of :shared:ui:navigation.

## Invariants

- No `test/` and no `androidTest/`: navigation is exercised indirectly by `app/` UI tests. Adding
  tests here requires creating the source set first.

## Side Effects

- None.

# Design Module Source Roots

## Overview
The source root of `:temp:old-design`. Only `main` exists.

## Subdirectories & Boundaries
- [main](./main/EGM.md) - Main source set of `:temp:old-design`.

## Invariants
- Carries no declarations of its own; it exists so the package path matches `com.ivy.design`.
- There is no `test` sibling: this module has no unit tests, which is why verification for it is a compile rather than a test run.

## Side Effects
- None.

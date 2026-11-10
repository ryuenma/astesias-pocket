# Design Module Main Source Set

## Overview
The production source set of `:temp:old-design`.

## Subdirectories & Boundaries
- [java](./java/EGM.md) - Java source root of `:temp:old-design`.

## Invariants
- Carries no declarations of its own; it exists so the package path matches `com.ivy.design`.
- There is no `test` sibling: this module has no unit tests, which is why verification for it is a compile rather than a test run.

## Side Effects
- None.

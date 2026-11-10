# Design Module Source Root

## Overview
The Java/Kotlin source root of `:temp:old-design`. Every production file sits below it under `com/ivy/design`.

## Subdirectories & Boundaries
- [com](./com/EGM.md) - Package chain (`com`) under the `:temp:old-design` source root.

## Invariants
- Carries no declarations of its own; it exists so the package path matches `com.ivy.design`.
- There is no `test` sibling: this module has no unit tests, which is why verification for it is a compile rather than a test run.

## Side Effects
- None.

# Design Module `com` Package Chain

## Overview
Package chain (`com`) under the `:temp:old-design` source root; only `ivy` is present.

## Subdirectories & Boundaries
- [ivy](./ivy/EGM.md) - Top-level package chain of `:temp:old-design`; only `design` is present.

## Invariants
- Carries no declarations of its own; it exists so the package path matches `com.ivy.design`.
- There is no `test` sibling: this module has no unit tests, which is why verification for it is a compile rather than a test run.

## Side Effects
- None.

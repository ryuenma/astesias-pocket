# Legacy Test Source Root

## Overview
The Java/Kotlin root of the `:temp:legacy-code` test source set. It contains only the `com` package chain down to `com.ivy.legacy`.

## Subdirectories & Boundaries
- [com](./com/EGM.md) - Package chain (`com`) under the test source root.

## Invariants
- Nothing is declared at this level; excluding this directory from a search excludes every test in the module.

## Side Effects
- None.

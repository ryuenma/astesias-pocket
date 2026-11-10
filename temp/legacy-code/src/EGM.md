# Legacy Module Source Roots

## Overview
`main` holds the production legacy code; `test` holds its JVM unit tests.

## Subdirectories & Boundaries
- [main](./main/EGM.md) - Main source set of `:temp:legacy-code`.
- [test](./test/EGM.md) - Unit-test source set of `:temp:legacy-code`.

## Invariants
- The two trees mirror each other's package paths, so a test's package can be read off its path - unlike the production tree, where packages and directories disagree.

## Side Effects
- None.

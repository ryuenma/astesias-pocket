# search source sets

## Overview
Source-set root of `:feature:search`. Production code lives entirely under `main/`; there is no
capability or variant layer between `src/` and the package tree. `main/` contains no
`AndroidManifest.xml` — the module manifest is contributed by the build plugin.

## Subdirectories & Boundaries
- [main](./main/EGM.md) - production source set; all Kotlin code of the module.
- `test/` - unit tests and Paparazzi snapshot outputs (`snapshots/`); test code is excluded from the contract set, so there is no contract file here.

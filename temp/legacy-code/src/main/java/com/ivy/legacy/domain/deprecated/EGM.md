# Deprecated Domain Code

## Overview
Superseded domain code kept because callers still exist. It is live, not dead: the creators here are the write path behind the legacy screens.

## Subdirectories & Boundaries
- [logic](./logic/EGM.md) - Creator/logic classes (`AccountCreator`, `CategoryCreator`, `PlannedPaymentsLogic`, `PreloadDataLogic`, ...) holding the real write paths.

## Invariants
- No production code lives directly in this directory - it is a grouping level, so its contract is the set of sub-directories listed above.
- Nothing here is dead code: `deprecated` marks the migration status of the API, not the reachability of the caller. The creators inside are the live write path for the remaining legacy screens.

## Side Effects
- None of its own: this directory contributes no runtime behaviour, only the contract of the packages below it.

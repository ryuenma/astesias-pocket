# ci-actions:base — source sets

## Overview
Source-set root of the automation base library: one production set and one test set.

## Subdirectories & Boundaries
- `main`: `./main/EGM.md` — production code (arg parsing, Arrow helpers, GitHub service, Ktor scope)
- `test`: instrumentation-free Kotlin tests for the base library; no contract (test-only directory)

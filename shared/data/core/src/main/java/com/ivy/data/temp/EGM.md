# com.ivy.data.temp — temporary migration support

## Overview

A holding area for helpers that exist only during a migration between representations. Currently one
subpackage of transaction-accessor shims.

## Interface

- `migration/` → [./migration/EGM.md](./migration/EGM.md)

## Subdirectories & Boundaries

- [migration/](./migration/EGM.md) — Temporary entity-shaped accessors over the sealed Transaction model (value, account id, type) plus a non-deterministic settleNow().

## Invariants

- Nothing here is meant to survive: code in `temp/` is scaffolding that should be deleted once the
  migration it supports lands. It must not become a dependency of new production code.

## Side Effects

- Inherited from subpackages (`settleNow` reads the clock).

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

# com.ivy.data (test) — data-core test fixtures

## Overview

Shared fixtures and property-test generators for the data layer, plus the subpackages that test each
subsystem.

## Interface

- `fun Arb.Companion.accountEntity(...)`-style generators in `ArbAccountEntity.kt` (1.3 KB).
- `ArbTransactionEntity.kt` (5.5 KB) — kotest `Arb` generators for `TransactionEntity`.
- `TestResourceUtil.kt` (652 B) — helper for loading test resources.
- Subpackages: `backup/`, `category/`, `dao/`, `db/`, `file/`, `profile/`, `repository/`, `sync/` —
  outside this partition; no contract files.

## Invariants

- The arbiters produce **entities**, not domain objects: data-layer tests assert against the persisted
  shape, and the domain generators live in `shared/domain/src/test`.
- `ArbTransactionEntity` must keep producing rows that satisfy the entity invariants (valid `type`,
  non-null id, coherent `dateTime`/`settled`), otherwise property tests pass on data the mapper would
  reject.

## Side Effects

- None: generators are pure and in-memory.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

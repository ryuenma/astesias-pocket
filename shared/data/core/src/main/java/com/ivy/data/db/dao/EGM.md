# com.ivy.data.db.dao — DAO split by direction

## Overview

Every table is reached through a **pair** of DAO interfaces: a read DAO (`AccountDao`) and a write
DAO (`WriteAccountDao`), plus an in-memory fake implementing both. The split is what lets
`RepositoryMemo` wrap exactly the writes it must invalidate, and keeps a read-only consumer from
being able to mutate.

## Interface

- `read/` → [./read/EGM.md](./read/EGM.md) — 17 `@Query` interfaces.
- `write/` → [./write/EGM.md](./write/EGM.md) — 14 `Write…Dao` interfaces.
- `fake/` → [./fake/EGM.md](./fake/EGM.md) — 10 in-memory doubles implementing both sides.

## Subdirectories & Boundaries

- [fake/](./fake/EGM.md) — In-memory @VisibleForTesting implementations of the read+write DAO pairs, one per table, so repositories can be exercised without Room.
- [read/](./read/EGM.md) — 17 read-only Room DAOs covering accounts, budgets, categories, exchange rates, loans, planned payments, settings, tags, users, the directory cache, the transfer/bill pipeline and transactions.
- [write/](./write/EGM.md) — 14 write-only Room DAOs: @Upsert save/saveMany/deleteById/deleteAll for entity tables, composite-key insert-replace for tag associations, and status-transition writers for the transfer/bill pipeline.

## Invariants

- **A read DAO must contain no mutating statement.** Every write lives on the `Write…` interface so
  `RepositoryMemo` can pair each write with its `DataWriteEvent`; a write hidden in a read DAO emits
  no event and leaves every memoised read stale.
- There is no `WriteDirectoryAccountDao`, no `WriteUserDao` and no `WritePendingTransferDao`: those
  tables are written by their own subsystems (`DirectorySyncRepository`, sync, transfer inbox) or are
  not written at all. Absence is intentional, not an oversight.
- DAO methods are `suspend` and execute on Room's executor; none of them take a dispatcher parameter.

## Side Effects

- Every method executes SQL against the open database.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

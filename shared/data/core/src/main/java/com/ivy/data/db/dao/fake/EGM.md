# com.ivy.data.db.dao.fake — in-memory DAO doubles

## Overview

`@VisibleForTesting` in-memory implementations of the DAO pairs, backed by a `mutableMap` keyed by
primary key. They exist so repositories can be tested on the JVM without Room.

## Interface

- `FakeAccountDao : AccountDao, WriteAccountDao`
- `FakeBudgetDao`, `FakeCategoryDao`, `FakeLoanDao`, `FakeLoanRecordDao`, `FakePlannedPaymentDao`,
  `FakeSettingsDao`, `FakeTagDao`, `FakeTagAssociationDao`, `FakeTransactionDao` (12.2 KB) — each
  implements both the read and the write interface for its table.

## Invariants

- **A fake must implement the same rule as the SQL, or a green test lies.** `FakeAccountDao.findByName`
  documents this explicitly: it uses `firstOrNull` over live rows rather than a last-wins overwrite, so
  a test seeded with two same-named accounts behaves like `LIMIT 1`, and it invents no ordering rule
  the database does not have.
- Fakes implement **both** DAO interfaces, so they satisfy the `@Provides` signatures in `RoomDbModule`
  and can be swapped in wholesale.
- Fakes live in `main`, not `test`, because other modules' tests consume them; they must therefore
  never be wired into a production Hilt graph.

## Side Effects

- None: state is per-instance and in memory, discarded when the fake is collected.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

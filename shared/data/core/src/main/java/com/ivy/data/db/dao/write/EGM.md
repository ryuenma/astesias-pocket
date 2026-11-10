# com.ivy.data.db.dao.write — mutation DAOs

## Overview

Fourteen write-only Room DAOs. Each write is paired by the repository with a `DataWriteEvent`, which
is why writes are isolated on their own interfaces.

## Interface

- `WriteAccountDao`, `WriteBudgetDao`, `WriteCategoryDao`, `WriteExchangeRatesDao`, `WriteLoanDao`,
  `WriteLoanRecordDao`, `WritePlannedPaymentRuleDao`, `WriteSettingsDao`, `WriteTagDao`,
  `WriteTransactionDao` — `@Upsert save(value)`, `@Upsert saveMany(values)`, `deleteById`, `deleteAll`.
- `WriteTagAssociationDao` — `@Insert(onConflict = REPLACE)` for the composite `(tagId, associatedId)`
  key, plus `deleteId`, `deleteAssociationsByTagId`, `deleteAssociationsByAssociateId`, `deleteAll`.
- `WritePendingBillDao` (3.9 KB) and `WriteTransferConfirmationDao` (3.5 KB),
  `WriteTransferOutboxDao` (5.1 KB) — the pipeline tables' write side, with status transitions rather
  than plain upserts.

## Invariants

- `@Upsert` is used for entity tables — insert-or-replace by primary key. `WriteTagAssociationDao`
  cannot use it because its identity is the composite key, so it uses
  `@Insert(onConflict = REPLACE)` instead; replacing the strategy changes delete semantics.
- `deleteById` is a hard `DELETE`, not a soft delete. Soft deletion is a field on the entity
  (`isDeleted`, `@Deprecated` for cloud sync), and mixing the two makes tombstones unrecoverable.
- `deleteAll` truncates a whole table. Callers must be sure they mean the current profile: there is no
  profile column to scope it, isolation comes from the database file.
- Saving through a write DAO *without* going through `RepositoryMemo` still mutates the database — it
  just emits no event, leaving every memoised reader stale.

## Side Effects

- Executes SQL writes on the Room executor; invalidates Room's invalidation tracker, so any `Flow`
  derived from these tables re-emits.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

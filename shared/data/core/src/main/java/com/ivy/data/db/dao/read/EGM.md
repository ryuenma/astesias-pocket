# com.ivy.data.db.dao.read — query DAOs

## Overview

Seventeen read-only Room DAOs, one per table, all `@Query`-only. They are the only place SQL
predicates for reads are written.

## Interface

- `AccountDao` — `findAll(deleted: Boolean = false)` (ordered by `orderNum ASC`), `findById(UUID)`,
  `findByName(String)`, `findMaxOrderNum()`.
- `BudgetDao`, `CategoryDao`, `ExchangeRatesDao`, `LoanDao`, `LoanRecordDao`,
  `PlannedPaymentRuleDao`, `SettingsDao`, `TagDao`, `TagAssociationDao`, `UserDao` — per-table finds.
- `DirectoryAccountDao` — `findByProfile`, `findOthers(ownProfileId)`.
- `PendingBillDao`, `PendingTransferDao`, `TransferConfirmationDao`, `TransferOutboxDao` — the
  transfer/bill pipeline's read side.
- `TransactionDao` (9.3 KB) — the largest: `findAll`, income/expense/transfer by account, transfers
  to account, between-dates variants, due-date variants, by-category variants, unspecified-category
  variants, by recurring rule, by id(s).

## Invariants

- `AccountDao.findByName` is an **exact, case-sensitive, live-only** match (`name = :name AND
  isDeleted = 0 LIMIT 1`) on purpose: two accounts called "Cash" is an ordinary state, so refusing to
  guess beats picking one. Do not soften it to `LIKE` or `COLLATE NOCASE`.
- Read queries filter `isDeleted = 0` by default (`findAll(deleted = false)`); passing `true` is the
  only way to see tombstoned rows, and it exists for sync, not for UI.
- `findAll` orders by `orderNum ASC` — the reorder contract from `com.ivy.data.model.Reorderable`.
- Soft-deleted rows are still rows: a query that forgets the flag double-counts in balance maths.

## Side Effects

- Executes SQL reads on the Room executor; no mutation.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

# com.ivy.data.db.migration — schema migrations

## Overview

Thirty-two hand-written Room `Migration`s covering versions 105 → 138. Each class is
`Migration(from, to)` and is registered in `IvyRoomDatabase`; the range is contiguous from 105 to 138
except `121 → 122`, which is an `AutoMigration`.

**Package split:** the classes for `105→…→122` and `125→126` declare `package com.ivy.domain.db.migration`
(legacy), while `123→…→138` declare `package com.ivy.data.db.migration`. `Migration125to126_Tags.kt`
additionally carries `@file:Suppress("InvalidPackageDeclaration")` because its directory says `data`
but its package says `domain`. Imports must use the right one; both are correct and both compile.

## Interface

- `Migration105to106_TrnRecurringRules`, `…106to107_Wishlist`, `…107to108_Sync`, `…108to109_Users`,
  `…109to110_PlannedPayments`, `…110to111_PlannedPaymentRule`, `…111to112_User_testUser`,
  `…112to113_ExchangeRates`, `…113to114_Multi_Currency`, `…114to115_Category_Account_Icons`,
  `…115to116_Account_Include_In_Balance`, `…116to117_SalteEdgeIntgration`, `…117to118_Budgets`,
  `…118to119_Loans`, `…119to120_LoanTransactions`, `…120to121_DropWishlistItem`,
  `…122to123_ExchangeRates`, `…125to126_Tags` → package `com.ivy.domain.db.migration`.
- `Migration123to124_LoanIncludeDateTime`, `…124to125_LoanEditDateTime`, `…126to127_LoanRecordType`,
  `…127to128_PaidForDateRecord`, `…128to129_DeleteIsDeleted`, `…129to130_LoanIncludeNote`,
  `…130to131_CrossProfileTransfers`, `…131to132_CategorySubcategories`, `…132to133_PendingTransfers`,
  `…133to134_BillReminderDueDay`, `…134to135_PendingBills`, `…135to136_TransferOutbox`,
  `…136to137_TransferConfirmations`, `…137to138_DirectoryAccountProfileKey` → package
  `com.ivy.data.db.migration`.

## Invariants

- **Migrations are append-only and immutable once shipped.** Editing a released migration means
  devices that already ran it keep the old data shape; the only safe fix is a new migration.
- Each `Migration(from, to)` must chain: a gap fails `IvyRoomDatabaseMigrationTest`, and Room throws
  `IllegalStateException` ("A migration from X to Y was required but not found") on open.
- Migrations must produce **exactly** the schema Room expects for the target version, otherwise
  `validateMigration` fails. When in doubt, follow the four-step rebuild Room itself uses
  (`CREATE …_new`, `INSERT … SELECT`, `DROP`, `RENAME`) as
  `Migration137to138_DirectoryAccountProfileKey` does.
- `Migration137to138` documents a real defect it closes: `directory_accounts` used `accountId` as its
  sole primary key while the server's uniqueness is `(profileId, accountId)`, so `INSERT OR REPLACE`
  collapsed rows across profiles. It also documents that the old `profileId` index is deliberately
  **not** recreated, because `profileId` now leads the primary key.
- A migration that rebuilds a table drops its indexes; re-create the ones that are still needed and
  only those.

## Side Effects

- Each `migrate(db: SupportSQLiteDatabase)` executes DDL/DML inside Room's migration transaction; a
  throw rolls the upgrade back and leaves the app unable to open the database.

## Verification

- Run: `./gradlew :shared:data:core:connectedDebugAndroidTest` (`IvyRoomDatabaseMigrationTest`)

# com.ivy.data.db.entity — Room entities

## Overview

Seventeen `@Entity` data classes. Most are also `@Keep` and `@Serializable` because the same class is the
JSON shape used by backups and by sync payloads, but the sync-local tables deliberately opt out of
serialization, and counts are not uniform: 12/17 are `@Serializable`, 15/17 are `@Keep`.

## Interface

- `AccountEntity`, `CategoryEntity`, `TransactionEntity`, `TagEntity`, `TagAssociationEntity`,
  `SettingsEntity`, `BudgetEntity`, `ExchangeRateEntity`,
  `LoanEntity`,
  `LoanRecordEntity`, `PlannedPaymentRuleEntity`, `UserEntity`, `DirectoryAccountEntity`,
  `PendingBillEntity`, `PendingTransferEntity`, `TransferOutboxEntity`, `TransferConfirmationEntity`.
- `TransactionEntity` (3.6 KB) is the richest: `accountId`, `type`, `amount`, `toAccountId`,
  `toAmount`, `title`, `description`, `dateTime`, `categoryId`, `dueDate`, `recurringRuleId`,
  `paidForDateTime`, `attachmentUrl`, `counterpartProfileId`, `linkedTransactionId`, `loanId`,
  `loanRecordId`, `isSynced` (deprecated), `isDeleted` (deprecated), `id`.

## Invariants

- **Room column names come from the property name (or `@ColumnInfo`), not `@SerialName`.** `@SerialName`
  is the *wire* name used by kotlinx.serialization for backups and sync payloads. Renaming a property
  renames a column and forces a migration; renaming `@SerialName` only changes the JSON shape. Schema
  `138.json` shows `tags.dateTime` where `@SerialName` says `creationTime`.
- **`@SerialName` is the wire name.** Renaming a property is safe for JSON; renaming its `@SerialName`
  breaks every existing backup file and sync payload.
- `@Keep` on every entity is required: R8 stripping a serializable member produces empty/default
  values at runtime instead of a compile error. (15/17 carry it; sync-local tables opt out.)
- `@Serializable(with = KSerializerUUID::class)` / `KSerializerInstant::class` are attached per field,
  so a new id or timestamp column must carry the same annotations or the JSON shape changes to
  arrays/objects.
- `isSynced` and `isDeleted` are `@Deprecated("Obsolete field used for cloud sync. Can't be deleted
  because of backwards compatibility")` — they must stay in the class and in the JSON.
- `TransactionEntity.counterpartProfileId` is `null` for ordinary and intra-profile transfers; its
  presence is the only thing distinguishing "moved between my accounts" from "moved to my SO".
- `TransactionEntity.linkedTransactionId` carries the cross-profile correlation id, but nothing queries
  it (`transactions` has `pk:['id']`, `indices: []`). The real idempotency guard is
  `pending_transfers.correlationId` (primary key) plus the PENDING→terminal transition.
- **Idempotency primary keys of the four transfer tables** (the feature's actual idempotency story):
  `pending_transfers.correlationId` (PK), `transfer_outbox` (PK), `transfer_confirmations` (PK),
  `directory_accounts` composite PK `(profileId, accountId)` — see
  `Migration137to138_DirectoryAccountProfileKey`, which rebuilt the table for it.
- `directory_accounts` has the composite primary key `(profileId, accountId)`.

## Side Effects

- None: entity instances are plain data; persistence happens through the DAOs.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

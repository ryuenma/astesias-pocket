# CSV Import Domain (com.ivy.importdata.csv.domain)

## Overview
The parsing and persistence half of the CSV mapper: converts mapped CSV rows into
`Transaction`/`Account` domain objects, creating missing accounts and resolving
currencies, then writes them in a batch. Parsing is a pure layer; the importer is
the only part that touches the database.

## Interface
- `CSVImporterV2` — `@Inject` class; injects `SettingsDao`, `TransactionRepository`, `TransactionMapper`, `AccountDao`, `CategoryRepository`, `CurrencyRepository`, `AccountRepository`, `TimeConverter`, …; maps rows to transactions and saves them.
- `ParseFields.kt` — parse functions used per column type: `parseAmount(value, metadata: Int)` (multiplier), `parseTransactionType(value, metadata: TrnTypeMetadata)`, `parseDate(value, metadata: DateMetadata)`, `parseAccount`, `parseAccountCurrency`, `parseToAccount`, `parseToAccountCurrency`, `parseCategory`, `parseTitle`, `parseDescription`, plus `String.tryMeta(metaContains: String)`.
- `ParseStatus.kt` — `tryStatus { … } -> MappingStatus` and `mappingFailure(): MappingStatus`.

## Invariants
- Parse functions return `null` on failure instead of throwing; a `null` result becomes an unsuccessful `MappingStatus`, never a crash mid-import.
- `parseAmount` applies the user's multiplier, so "amount × 100" (cents) is handled by the mapper, not by the CSV.
- `parseTransactionType` matches the configured income/expense/transfer strings from `TrnTypeMetadata`; an unmapped transfer string means the row cannot be a transfer.
- `parseDate` tries the formats allowed by the chosen `DateMetadata` and rejects anything else.
- Account names are trimmed and rejected when blank (`notBlankTrimmedString`); the base currency from `settingsDao.findFirst().currency` is the fallback for rows without a currency column.
- New accounts created during import get `orderNum` from `accountDao.findMaxOrderNum().nextOrderNum()`, so imported accounts append instead of colliding.
- The in-memory account list is refreshed after each created account, so a file repeating the same new account does not create duplicates.

## Side Effects
- Database writes: `transactionRepository.save(...)` per imported row, `accountRepository.save(...)` for accounts that did not exist, category lookups through `CategoryRepository`.
- Reads settings, currencies and existing accounts from the DAOs.
- No network access.

## Verification
- Run: `./gradlew :feature:import-data:testDebugUnitTest` (unit tests in `src/test`).

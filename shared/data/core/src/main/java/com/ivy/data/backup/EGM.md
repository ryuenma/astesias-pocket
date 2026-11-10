# com.ivy.data.backup — backup export/import

## Overview

The full-database backup format and its importer/exporter. A backup is one JSON document holding every
entity table plus a `sharedPrefs` map; `ImportResult` reports what an import did, including failures and
duplicate-name warnings that the fork added.

## Interface

- `class BackupDataUseCase @Inject constructor(...)` (21 KB) —
  `suspend fun exportToFile(...)`, `suspend fun generateJsonBackup(): String`,
  `suspend fun importBackupFile(...)`, `suspend fun importJson(...)`.
- `@Keep @Serializable data class AstesiaWalletCompleteData` (`typealias IvyWalletCompleteData`) —
  `accounts`, `budgets`, `categories`, `loanRecords`, `loans`, `plannedPaymentRules`, `settings`,
  `transactions`, `sharedPrefs: HashMap<String, String>`, `tags`, `tagAssociations`; **every field
  defaults to `emptyList()`/`HashMap()`**.
- `data class ImportResult(rowsFound, transactionsImported, accountsImported, categoriesImported,
  failedRows: ImmutableList<CSVRow>, errorMessage: String? = null,
  duplicateNameWarnings: ImmutableList<String> = persistentListOf())` with `val isSuccess get() = errorMessage == null`.
- `data class CSVRow(index: Int, content: List<String>)`.

## Invariants

- **The JSON field names are the backup format.** `@SerialName` values (`"accounts"`, `"sharedPrefs"`,
  `"tagAssociations"`, …) must not change: existing files would import as empty. The `typealias
  IvyWalletCompleteData` keeps the old name compiling.
- Every collection field has a **default**, so a backup written by an older version (missing `tags`,
  `budgets`, …) still deserialises. A field without a default makes old backups fail to parse entirely.
- `@Keep` is required for R8; without it the serializable members are stripped and the export is empty.
- `ImportResult.isSuccess` is `errorMessage == null` — a caller must check `errorMessage` and surface
  it. Before the fork existed, every failure path returned an all-zero result and the UI said
  "0 imported", making a corrupted or wrongly-encoded backup indistinguishable from an empty one.
- `duplicateNameWarnings` is **not** an error: the import succeeded and the data is intact, but a name
  occurring three or more times could not be reconciled and may now exist twice. Reporting it is
  required; hiding it silently loses the information.
- `sharedPrefs` is exported as `String` values only — a preference stored as another type round-trips
  through its string form.

## Side Effects

- Writes the backup through `FileSystem` to a user-chosen URI (UTF-16 text).
- Import writes to every table via the DAOs/repositories and can replace existing rows.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`
- Real-file import: `./gradlew :shared:data:core:connectedDebugAndroidTest`

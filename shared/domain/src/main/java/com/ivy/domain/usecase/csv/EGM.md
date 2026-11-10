# com.ivy.domain.usecase.csv — CSV export/import

## Overview

Exports transactions to CSV, reads CSV back, and models one exported row.

## Interface

- `class ExportCsvUseCase @Inject constructor(...)` — `suspend fun exportToFile(...)`,
  `suspend fun exportRows(...)`, `suspend fun exportCsv(...)`.
- `class ReadCsvUseCase @Inject constructor()` — `fun readCsv(csv: String): List<List<String>>`.
- `data class IvyCsvRow(...)` (8.4 KB file, `@SuppressWarnings("DataClassTypedIDs")`).

## Invariants

- The CSV **column order and header names are the export format**: an importer (including
  `BackupDataUseCase`'s legacy CSV path and external tools like Ivy Wallet/money-manager) reads by
  position, so reordering columns silently corrupts every import.
- `ReadCsvUseCase.readCsv` returns raw `List<List<String>>` with **no header handling and no escaping
  validation** — parsing into a typed row is the caller's job, and a malformed file yields ragged lists
  rather than an error.
- `IvyCsvRow` suppresses `DataClassTypedIDs`: its ids are raw `UUID`s, unlike the domain model's typed
  ids. Do not "fix" one field without fixing the parser that builds it.
- Export writes through `FileSystem`, so the encoding is UTF-16 for the string path — see
  `com.ivy.data.file`. Import must therefore pass an explicit charset.

## Side Effects

- `exportToFile` writes to a user-chosen URI through `ContentResolver`.
- Reads the transaction repository (`dispatchers.io`).

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

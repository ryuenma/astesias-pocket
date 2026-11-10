# com.ivy.domain.usecase.xlsx — spreadsheet export

## Overview

Exports transactions to a real `.xlsx` workbook without Apache POI. `XlsxWriter` builds the OOXML ZIP
parts directly, because POI drags a desktop classpath onto Android.

## Interface

- `class ExportXlsxUseCase @Inject constructor(...)` — `suspend fun exportToFile(...)`.
- `object XlsxWriter` — `fun write(rows: List<List<String?>>, sheetName: String = "Transactions"): ByteArray`.
- Private XML part constants: `CONTENT_TYPES`, `ROOT_RELS`, `WORKBOOK`, worksheet and styles parts.

## Invariants

- **`write` returns a `ByteArray`, and it must be written through `FileSystem.writeToFile(uri, ByteArray)`.**
  The `String` overload writes UTF-16 and would corrupt the ZIP; the failure would surface as "the file
  will not open in Excel", not as an error.
- Cells use inline strings (`t="inlineStr"`) rather than a shared-strings table. That is deliberate: the
  shared table only pays off on heavy string repetition, and transaction descriptions do not repeat
  enough to justify it.
- Supported feature set is fixed: **no formulas, no styling beyond a bold header, no multiple sheets.**
  Adding any of those means extending the part templates, not post-processing the bytes.
- `sheetName` defaults to `"Transactions"` and is written into `workbook.xml`; Excel rejects a sheet name
  containing `[]:*?/\` or exceeding 31 characters, so a caller-supplied name is not validated here.
- `null` cells are representable (`List<String?>`) and are emitted as empty cells, not as the text
  `"null"`.

## Side Effects

- `exportToFile` writes binary data to a user-chosen URI via `ContentResolver`.
- Reads the transaction repository.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

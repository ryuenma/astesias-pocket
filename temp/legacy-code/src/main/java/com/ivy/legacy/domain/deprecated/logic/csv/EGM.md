# CSV Import Pipeline

## Overview
Three collaborator classes that turn a CSV file into transactions: read/normalise, map rows, then insert. Packages are mixed - `CSVImporter.kt` declares `com.ivy.wallet...`, while `CSVMapper`/`CSVNormalizer` declare `com.ivy.legacy...`.

## Interface
- `CSVImporter(...)` - the orchestrator (553 lines; this contract covers its first 300).
- `CSVMapper(...)` - maps a parsed row into the model types.
- `CSVNormalizer(...)` - normalises raw CSV values (header casing/whitespace) before mapping.
- Model types live in `./model`.

- [model](./model/EGM.md) - CSV import model: single-member `ImportType` and the `RowMapping`/`JoinResult` row types.

## Invariants
- `CSVImporter` is the only class that writes: it inserts the transactions it produced, so calling it twice on the same file duplicates data unless the caller de-duplicates.
- `CSVImporter.kt` exceeds the 300-line read cap; only its first 300 lines were read for this contract.
- The module depends on `libs.bundles.opencsv` (see `temp/legacy-code/build.gradle.kts`), so parsing assumptions must stay within that library's CSV dialect.

## Side Effects
- `CSVImporter` inserts transactions into the database through the repositories it is injected with.
- Reads a user-supplied file/stream; no network.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

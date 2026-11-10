# CSV Import — Column Mapping (com.ivy.importdata.csv)

## Overview
The interactive CSV mapper: the user picks a file, maps each CSV column to an Ivy
field (amount, type, date, account, …), and the screen shows a live preview of the
parsed rows before the import runs. Parsing/DB work is delegated to
`csv/domain`; this package is the state machine plus UI.

## Interface
- `CSVEvent` — sealed interface: `FilePicked(uri)`, `MapAmount`, `AmountMultiplier`, `MapType`, `TypeMetaChange`, `MapDate`, `DataMetaChange`, `MapAccount`, `MapAccountCurrency`, `MapToAccount`, `MapToAccountCurrency`, `MapToAmount`, `ToAmountMetaChange`, `MapCategory`, `MapTitle`, `MapDescription`, `Continue`, `ResetState`, `FinishImport(launchedFromOnboarding, onboardingViewModel)`.
- `CSVState` — `uiState: UIState` (`Idle` / `Processing(percent)` / `Result(importResult)`), `columns: CSVRow?`, `csv: ImmutableList<CSVRow>?`, `important: ImportantFields?`, `transfer: TransferFields?`, `optional: OptionalFields?`, `continueEnabled`.
- Mapping value types in `CSVState.kt`: `ImportantFields`, `TransferFields`, `OptionalFields`, `ColumnMapping<M>(ivyColumn, helpInfo, name, index, metadata, required)`, `MappingStatus(sampleValues, success)`, `TrnTypeMetadata(income, expense, transfer?)`, `DateMetadata`, `CSVRow(values)`.
- `CSVViewModel` — `@HiltViewModel`, plain `ViewModel`; injects `FileSystem`, `CSVImporterV2`, `Navigation`; exposes `uiState(): CSVState`.
- `CSVScreen(screen: CSVScreen)` — screen entry point; private `ImportUI`, `ImportButton`, `csvTable`, `CSVRow`, `CSVCell`, plus the `spacer8`/`Spacer8` helpers.
- Subdirectory: `./domain/EGM.md`.

## Invariants
- `continueEnabled` is derived from `ImportantFields` only: every required important column must be mapped with a `success` `MappingStatus`.
- Column mappings are keyed by CSV `index`; a column can be re-mapped at any time before `Continue`.
- `MappingStatus.sampleValues` is what the user sees to judge a mapping — it is a preview, not a validation artefact.
- `UIState.Processing(percent)` is the only place progress is reported; `Result` carries the `ImportResult` that the finish step renders.
- `ResetState` clears all mappings back to `Idle` without touching the DB.
- Transfer-only columns (`toAccount`, `toAccountCurrency`, `toAmount`) are optional for non-transfer files.

## Side Effects
- Opens the system file picker via `ivyContext.openFile { fileUri -> … }` and reads the picked URI through `FileSystem`.
- Runs the import through `CSVImporterV2` (database writes happen in the `domain` subpackage).
- Navigates with `Navigation` after finishing (`FinishImport`), including back into onboarding when `launchedFromOnboarding` is true.

## Verification
- Run: `./gradlew :feature:import-data:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

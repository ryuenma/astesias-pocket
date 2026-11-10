# Import Flow (com.ivy.importdata.csvimport)

## Overview
The guided import flow (import-from → instructions → loading → result) that runs on
first launch and from Settings. It uploads a file through the legacy
`CSVMapper`/`CSVNormalizer`/`CSVImporter` pipeline and reports an `ImportResult`.

## Interface
- `enum class ImportStep` — `IMPORT_FROM`, `INSTRUCTIONS`, `LOADING`, `RESULT`.
- `ImportViewModel` — `@HiltViewModel`, plain `ViewModel`; injects `IvyWalletCtx`, `Navigation`, `FileSystem`, `CSVNormalizer`, `CSVMapper`, `CSVImporter`, `BackupDataUseCase`. LiveData: `importStep`, `importType`, `importProgressPercent`, `importResult`.
- `start(screen: ImportScreen)` — installs `nav.onBackPressed[screen]` so back walks the steps backwards.
- `uploadFile(context)`, `setImportType(type)`, `skip(screen, onboardingViewModel)`, `finish(screen, onboardingViewModel)`.
- `BoxWithConstraintsScope.ImportCSVScreen(screen: ImportScreen)` — screen entry point; private `UI(...)`, `Preview`.
- Subdirectory: `./flow/EGM.md`.

## Invariants
- Back navigation is step-aware: `IMPORT_FROM` exits (returns false), `INSTRUCTIONS` and `RESULT` return to `IMPORT_FROM`, `LOADING` swallows back (returns true) so an import in progress cannot be abandoned mid-write.
- `uploadFile` returns early when no `importType` is selected — the flow cannot start without a chosen source.
- Progress (`importProgressPercent`) is only meaningful between `LOADING` and `RESULT`.
- `skip` and `finish` both hand control back to `OnboardingViewModel`, so the onboarding state machine stays authoritative when the flow was launched from onboarding.

## Side Effects
- Opens the system file picker (`ivyContext.openFile`), reads the URI through `FileSystem`.
- Runs the legacy import pipeline (`CSVNormalizer` → `CSVMapper` → `CSVImporter`), which writes transactions/accounts to the database.
- Uses `BackupDataUseCase` for the backup import variant.
- Wraps work in `TestIdlingResource` so instrumentation tests can wait.
- Navigates with `Navigation`.

## Verification
- Run: `./gradlew :feature:import-data:testDebugUnitTest` (unit tests in `src/test`).

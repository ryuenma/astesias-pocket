# Import Flow UI (com.ivy.importdata.csvimport.flow)

## Overview
Stateless composables for the import steps: the "import from" chooser, the
processing indicator, the result screen and the step-list helpers. Nothing here
holds state; each screen takes values and callbacks.

## Interface
- `BoxWithConstraintsScope.ImportFrom(hasSkip, onSkip, ...)` — source chooser screen; private `ImportOption(importType, onImportFrom)`.
- `ImportProcessing(progressPercent: Int)` — progress screen; private `ProgressBar(progressPercent)`.
- `ImportResultUI(result: ImportResult, onTryAgain: (() -> Unit)? = null, ...)` — result screen; private `SuccessSectionUI`, `FailedSectionUI`.
- `fun ImportType.ImportSteps(onUploadClick: () -> Unit, …)` — dispatches to the per-type instruction content (see `./instructions/EGM.md`).
- `internal fun failedRowsMessage(result: ImportResult): String?` — user-facing summary of failed rows, `null` when nothing failed.
- Subdirectory: `./instructions/EGM.md`.

## Invariants
- All composables are stateless: no `remember`, no VM lookups; state arrives from `ImportViewModel` through the parent `ImportCSVScreen`.
- `failedRowsMessage` returns `null` when there are no failures, so callers must null-check before rendering the failure block.
- `ImportProcessing` clamps the percentage into the `ProgressBar`'s 0..1 progress range.
- `onTryAgain` is optional: the success path passes `null` and therefore renders no retry affordance.

## Side Effects
- None. Pure rendering plus callback invocation.

## Verification
- Run: `./gradlew :feature:import-data:testDebugUnitTest` (unit tests in `src/test`).

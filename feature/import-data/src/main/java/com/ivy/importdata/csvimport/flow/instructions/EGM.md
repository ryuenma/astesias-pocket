# Import Instructions (com.ivy.importdata.csvimport.flow.instructions)

## Overview
Per-source instruction pages for the import flow: how to export a CSV from a bank or
from another wallet app. `ImportInstructions` renders the generic page;
`IvyWalletSteps` and `DefaultImportSteps` provide the wallet-specific step lists.

## Interface
- `BoxWithConstraintsScope.ImportInstructions(hasSkip, importType, onSkip, ...)` — instruction screen with the video/article links and step buttons.
- `fun IvyWalletSteps(onUploadClick: () -> Unit, …)` — step list for importing from the AstesiaWallet/Ivy wallet format (file name `AstesiaWalletSteps.kt`, function name retained as `IvyWalletSteps`).
- `DefaultImportSteps(videoUrl: String? = null, articleUrl: String? = null, onUploadClick, …)` — generic steps used when no wallet-specific page exists.
- Shared building blocks: `VideoArticleRow(videoUrl, articleUrl)`, `VideoButton`, `ArticleButton`, `InstructionButton(icon, caption, text, onClick)`, `UploadFileStep(stepNumber, text, btnTitle, onUploadClick)`, `StepTitle(number, title, description?)`.

## Invariants
- `videoUrl` and `articleUrl` are nullable: when both are `null` the `VideoArticleRow` renders nothing and the page degrades to steps only.
- Step numbers are passed explicitly (`StepTitle(number = …)`, `UploadFileStep(stepNumber = …)`); they are display-only and must stay in visual order.
- These composables render instructions only — the upload action is always the caller's `onUploadClick`.

## Side Effects
- Opens external video/article URLs when the user taps those buttons; no other I/O.

## Verification
- Run: `./gradlew :feature:import-data:testDebugUnitTest` (unit tests in `src/test`).

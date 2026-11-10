# Compose Stability Checker

## Overview
Entry point of the Compose stability CI gate. Walks the repository for Compose compiler report
folders, parses each module's `*_composables.csv` (qualification flags) and `*_composables.txt`
(parameter stability) into `UnstableComposable` rows, drops anything already in the baseline and
prints a text report.

## Interface
- `fun main(args: Array<String>)` — the `application` entry point (`MainKt`)
- Constants: `OutputReportFileName = "ivy-compose-stability-report.txt"`, `ComposeReportFolderName = "compose_compiler"`, `BaselineArg = "generateBaseline"`, `BaselineFileName = "ivy-compose-stability-baseline.txt"`
- Private helpers: `findComposeReportFolders()`, `unstableComposables(reportFolder)`, `parseUnstableComposables(csv)`, `UnstableComposable.findUnstableArguments(txt)`, `buildIvyReport(...)`, `createReportFile(...)`, `generateBaseline(...)`, `readBaseline()`
- Subdirectories: `./model/EGM.md`

## Invariants
- The repository root is assumed to be `../../` from the working directory — the task only works when
  launched by Gradle from the module directory.
- CSV columns are positional: `[0]` FQN, `[1]` name, `[3]` skippable, `[4]` restartable; rows where
  `restartable && skippable` are dropped.
- Unstable arguments are extracted by substring match on `"unstable"` in the `.txt` parameter listing —
  a compiler report format change silently yields an empty argument set rather than an error.
- Composables whose FQN contains `ViewModel.` are always excluded.
- `generateBaseline` mode never fails the build: it rewrites the baseline file and prints its path.
- Non-baseline mode prints `SUCCESS!` and exits 0, or prints the count and `exitProcess(1)`.

## Side Effects
- Recursively walks the filesystem from `../../` (read-only scan for `compose_compiler` directories).
- Writes `ivy-compose-stability-report.txt` into the process working directory.
- With `generateBaseline`: overwrites `ivy-compose-stability-baseline.txt`.
- Calls `exitProcess(1)` to fail CI; prints the whole report to stdout.

## Verification
- Run: `./gradlew :ci-actions:compose-stability:run` (after `./gradlew assembleDemo -PcomposeCompilerReports=true`)

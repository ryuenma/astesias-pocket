# ci-actions:compose-stability

## Overview
CI check for Compose runtime stability. It reads the Compose compiler's per-module report folders
(`build/compose_compiler`), collects every `@Composable` that is not both restartable and skippable,
diffs that against a committed baseline and exits non-zero when new unstable composables appear.

`build.gradle.kts` applies `ivy.script` + `application` with `mainClass = "ivy.automate.compose.stability.MainKt"`.

## Interface
- Gradle project `:ci-actions:compose-stability`, task `run` (`./gradlew :ci-actions:compose-stability:run`)
- CLI flag `generateBaseline` — rewrite the baseline instead of failing
- Committed baseline: `ivy-compose-stability-baseline.txt` (one fully-qualified composable per line)
- Generated report: `ivy-compose-stability-report.txt` (workdir-relative, uploaded by CI)
- Contracts: `./src/EGM.md`

## Invariants
- The check is report-driven: it only sees modules whose compiler reports were produced with
  `-PcomposeCompilerReports=true` (`scripts/composeStability.sh` assembles `demo` first for that reason).
- A composable is "unstable" if `!restartable || !skippable`, and `*ViewModel*` composables are always
  filtered out.
- The baseline is a plain newline-separated text file; regenerating it requires the
  `generateBaseline` argument and is committed by `scripts/composeStabilityBaseline.sh`.
- Report/baseline file names are constants in `Main.kt`, so the workflow and the script both depend on
  them staying in sync.

## Side Effects
- Walks the repository tree from `../../` looking for `compose_compiler` folders (read-only).
- Writes `ivy-compose-stability-report.txt` into the working directory and, in baseline mode, rewrites
  `ivy-compose-stability-baseline.txt`.
- `exitProcess(1)` when unstable composables are found outside the baseline.

## Verification
- Run: `./gradlew :ci-actions:compose-stability:test`

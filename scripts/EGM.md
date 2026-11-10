# Developer Scripts

## Overview
Bash entry points for the checks CI runs, so a developer can reproduce each workflow locally from the
repository root. Every script is a thin wrapper around one or more Gradle tasks; a few also stage and
commit the artifact they regenerate (baselines).

## Interface
| Script | Gradle task | Purpose |
|---|---|---|
| `unitTests.sh` | `testDebugUnitTest` | JVM unit tests (documented as non-blocking: `exit 0`) |
| `integrationTests.sh` | `:shared:data:core:connectedDebugAndroidTest`, `:shared:domain:connectedDebugAndroidTest` | device/emulator instrumentation tests |
| `paparazziScreenshotTests.sh` | `verifyPaparazziDebug` | Paparazzi screenshot verification |
| `lint.sh` | `lintR` | lint, then opens `build/reports/lint/lint.html` |
| `lintBaseline.sh` | `lintR` (twice) | deletes `app/lint-baseline.xml`, regenerates, re-runs, commits |
| `detekt.sh` | `detekt` | static analysis |
| `detektFormat.sh` | `detektFormat -Ddetekt.filesToCheck=<changed .kt/.kts>` | auto-correct only changed files |
| `detektBaseline.sh` | `detektBaseline` | regenerates `config/detekt/baseline.yml` and commits |
| `composeStability.sh` | `assembleDemo -PcomposeCompilerReports=true`, then `:ci-actions:compose-stability:run` | fails on new unstable composables |
| `composeStabilityBaseline.sh` | `:ci-actions:compose-stability:run --args='generateBaseline'` | rewrites `ci-actions/compose-stability/ivy-compose-stability-baseline.txt` and commits |
| `generateModulesGraph.sh` | `generateModulesGraphvizText -Pmodules.graph.output.gv=all_modules.gv`, then `dot -Tsvg` | regenerates `docs/assets/modules-graph.svg` |

## Invariants
- All scripts except `detektFormat.sh` assert `settings.gradle.kts` exists and abort with
  "Please run this script from the root directory of the repo." — they must be run from the repo root.
- Scripts that produce a committed artifact (`lintBaseline.sh`, `detektBaseline.sh`,
  `composeStabilityBaseline.sh`) run `git add` + `git commit` themselves; they never push.
- `detekt.sh` and `detektBaseline.sh` swallow the Gradle exit status (`|| exit 0`).
- `generateModulesGraph.sh` requires the `dot` binary and deletes `all_modules.gv` before regenerating.
- Baselines are the intended "known debt" escapes: a check only fails on findings not already
  recorded in the corresponding baseline file.

## Side Effects
- Runs Gradle builds/tests (writes `build/` outputs, can take a long time, needs the Android SDK).
- Deletes and rewrites `app/lint-baseline.xml`, `config/detekt/baseline.yml`,
  `ci-actions/compose-stability/ivy-compose-stability-baseline.txt`, `all_modules.gv`,
  `docs/assets/modules-graph.svg`.
- Creates local git commits on the current branch in the baseline scripts.
- Opens a browser/report (`open build/reports/lint/lint.html`, `open docs/assets/modules-graph.svg`).

## Verification
- Run: `./scripts/unitTests.sh` from the repository root

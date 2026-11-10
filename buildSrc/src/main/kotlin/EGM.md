# Gradle Convention Plugins

## Overview
Precompiled script plugins that encode the repository's build conventions. Each `ivy.*.gradle.kts` file
is applied by id (`id("ivy.module")`) from module build scripts, so the plugin id is the module's
contract with the build. `versionCatalog.kt` exists only to expose the version catalog to these
precompiled scripts.

**Everything here is global**: changing one of these files changes how every module that applies it
compiles, tests and lints. Treat an edit as a repository-wide change, not a local one.

## Interface
| File / plugin id | Applies / adds |
|---|---|
| `ivy.kotlin-android.gradle.kts` | `com.android.library` + `kotlin-android`: jvm target and compile/min SDK from the catalog, 2048 MB test heap, Arrow/Kotlin/Timber bundles, test bundle |
| `ivy.hilt.gradle.kts` | `ivy.kotlin-android` + Hilt + KSP; adds `build/generated/ksp/<sourceSet>/kotlin` to every Kotlin source set, Hilt bundle + `hilt-compiler` |
| `ivy.module.gradle.kts` | `ivy.kotlin-android` + `ivy.hilt` + `ivy.kotlinx-serialization` — the default module baseline |
| `ivy.kotlinx-serialization.gradle.kts` | `ivy.kotlin-android` + serialization plugin + `kotlinx-serialization-json` |
| `ivy.compose.gradle.kts` | Compose compiler + `ivy.module` + molecule; enables `buildFeatures.compose`, disables `MissingTranslation`/`ComposeViewModelInjection` lint, `unitTests.isReturnDefaultValues = true`, writes compiler reports/metrics to `build/compose_compiler`, Compose bundle + Slack Compose lint checks |
| `ivy.feature.gradle.kts` | `ivy.module` + `ivy.compose` + `ivy.paparazzi` — the `:feature:*` preset |
| `ivy.widget.gradle.kts` | `ivy.module` + Compose; adds the Glance and activity bundles |
| `ivy.room.gradle.kts` | `ivy.module` + androidx Room; Room bundle + `room-compiler`, schema dir `$projectDir/schemas`, schemas as androidTest assets |
| `ivy.paparazzi.gradle.kts` | `ivy.compose` + `app.cash.paparazzi`; constrains Guava to the `STANDARD_JVM` variant (paparazzi layoutlib workaround) |
| `ivy.script.gradle.kts` | `kotlin("jvm")` + serialization for the `ci-actions` executables: `jvmToolchain`, `-XXLanguage:+ContextReceivers`, Arrow/Kotlin bundles, test bundle |
| `ivy.detekt.gradle.kts` | Detekt: config `config/detekt/config.yml`, baseline `config/detekt/baseline.yml`, registers `detektFormat`, honours `-Ddetekt.filesToCheck=`, HTML report only, `parallel`, `allRules`, Compose/ktlint/Ivy rulesets |
| `versionCatalog.kt` | `Project.libs`, internal `Project.catalog`, `VersionCatalog.version/bundle/library` helpers |

## Invariants
- Plugin ids are the public API: renaming a file renames the id and breaks every `id("ivy.*")` caller.
- `ivy.module` is the baseline almost every library applies; `ivy.feature` is `ivy.module` + Compose +
  Paparazzi, so a feature module cannot opt out of Compose.
- `ivy.script` is JVM-only (`kotlin("jvm")`, no Android) — it is what makes `ci-actions/*` runnable via
  `:ci-actions:<module>:run`.
- `-XXLanguage:+ContextReceivers` in `ivy.script` is required: the `ci-actions` sources use `context(Raise<String>)`.
- `ivy.hilt` registers the KSP output directory for **all** source sets — removing it breaks Hilt
  injection in every module.
- `ivy.detekt` sets `outputs.upToDateWhen { false }`: detekt always re-runs, so it is never cached.
- `ivy.paparazzi`'s Guava constraint must survive until the linked upstream issue is fixed; removing it
  breaks screenshot tests on the JVM.
- KSP is the annotation processor everywhere (no kapt) — `ksp(...)` dependencies only.
- Versions come from `gradle/libs.versions.toml` through `catalog.version(...)`; hard-coding a version
  here bypasses the catalog and will drift.

## Side Effects
- Executed at Gradle configuration time for every module that applies a plugin: mutates the module's
  `android {}`/`kotlin {}` extensions, source sets, dependencies and lint options.
- `ivy.compose` writes Compose compiler reports and metrics into `build/compose_compiler` — the input
  the `:ci-actions:compose-stability` check scans.
- `ivy.detekt` registers the `detektFormat` task (writes formatted sources when run).
- `ivy.room` registers `$projectDir/schemas` as the exported Room schema directory.

## Verification
- Run: `./gradlew help` (configures every module, i.e. exercises all convention plugins)
- Lint the scripts themselves: `./scripts/detekt.sh` includes `**/*.kts`

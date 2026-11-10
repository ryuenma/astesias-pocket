# feature/settings (`:feature:settings`)

## Overview
Settings screen. Gradle module `:feature:settings`, namespace `com.ivy.settings`, built with the `ivy.feature` plugin.
`SettingsScreen()` exposes currency, theme, lock, notifications, privacy toggles, sync state, transfer destinations and data deletion.

Dependencies (from `build.gradle.kts`): `projects.shared.base`, `projects.shared.data.core`, `projects.shared.domain`, `projects.shared.ui.core`, `projects.shared.ui.navigation`, `projects.temp.legacyCode`, `projects.temp.oldDesign`.

## Subdirectories & Boundaries
- [src](./src/EGM.md) - source-set root; this module has no `src/test` directory, so it has no unit-test or snapshot suite of its own.
- `build/` - Gradle build output (generated); not documented.

## Interface

- Gradle module `:feature:settings`; namespace `com.ivy.settings`.
- Contains 4 Kotlin source file(s) under `src/main` (test sources excluded).
- Convention plugins applied, from `build.gradle.kts`: `ivy.feature`.
- Child packages, each with its own contract:
  - [`src`](./src/EGM.md)

## Invariants

- Project-level dependencies declared in `build.gradle.kts`: none declared.
- Its namespace is the package root of every file below `src/main`; renaming it renames the public surface.
- Build convention plugins applied here govern all 34 modules that use the same convention; a change in one is a change in all of them.

## Side Effects

- At this level the module is a build unit: applying its `build.gradle.kts` changes the compiled output of everything it declares above, but the script itself performs no runtime work.
- Runtime side effects (database writes, network calls, UI rendering) live in the child packages documented below, and each states its own.

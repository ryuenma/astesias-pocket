# Inherited Ivy Code (`temp/`)

## Overview
The fork's holding area for inherited Ivy Wallet code that it **keeps and edits** rather than discards. Two Gradle modules live here: `:temp:legacy-code` (all the inherited app logic and the legacy Compose UI) and `:temp:old-design` (the inherited design system that legacy UI renders through). Neither is dead code - `app` depends on them and the remaining legacy screens run out of them.

## Subdirectories & Boundaries
- [legacy-code](./legacy-code/EGM.md) - Gradle module `:temp:legacy-code` (namespace `com.ivy.legacy`): inherited app logic, FRP layer, actions, pure domain functions and legacy Compose UI that the fork still runs.
- [old-design](./old-design/EGM.md) - Gradle module `:temp:old-design` (namespace `com.ivy.design`): inherited design system (theme, building blocks, components) still consumed by legacy screens.

## Invariants
- `temp/` is a name, not a status: "temporary" here means "inherited upstream", and both modules are on the app's live dependency graph. Deleting a directory because it says `temp` breaks the build.
- Module names in Gradle are `:temp:legacy-code` and `:temp:old-design` (`settings.gradle.kts`), so the directories are also the module paths - moving a directory means editing `settings.gradle.kts` and every `projects.temp.*` accessor.
- Namespaces (`com.ivy.legacy`, `com.ivy.design`) do not match most source packages in `legacy-code`, where `com.ivy.wallet.*`, `com.ivy.frp.*` and `com.ivy.domain.legacy.ui.*` all appear.
- `temp/legacy-code/schemas` exists on disk but is **empty** - no exported Room schema JSON is currently present - and it is outside this contract set, so it is not documented as a package.

## Side Effects
- Room schema/database wiring, DataStore (`ivy_wallet` preferences), WorkManager jobs (reminders, transfer sync), notifications and the ktor-based PocketBase sync client - all reached through `:temp:legacy-code`.
- `:temp:old-design` itself has no side effects: it is tokens and composables only.

# `temp/legacy-code` Module

## Overview
Gradle module `:temp:legacy-code`, namespace `com.ivy.legacy`. It is the inherited Ivy Wallet code that this fork keeps and edits: FRP core, actions, pure domain functions, deprecated creators, legacy data models, utilities, the legacy Compose UI and the notification/sync workers. It is a live module, not dead code - the app's remaining legacy screens run out of it.

## Interface
- Build script (`build.gradle.kts`): plugins `ivy.feature` and `ivy.room`; `android.namespace = "com.ivy.legacy"`.
- Project dependencies: `shared.base`, `shared.data.core`, `shared.domain`, `shared.ui.core`, `shared.ui.navigation`, `temp.old-design`.
- Library dependencies: activity, opencsv, ktor, androidx.work, datastore, keval, recyclerview; tests use the testing bundle plus `androidx.work.testing` (`TestListenableWorkerBuilder`, so the reminder worker's input data and run can be exercised on the JVM).
- Sub-packages: `com/ivy/legacy` (nearly everything) and `com/ivy/wallet` (notification ids).

- [src](./src/EGM.md) - Source roots of `:temp:legacy-code`: `main` (production legacy code) and `test` (JVM unit tests).

## Invariants
- `ivy.room` in the plugin block means this module owns Room schema/DAO wiring; moving a DAO out of it changes who compiles the schema.
- The namespace `com.ivy.legacy` does not match most source packages (`com.ivy.wallet.*`, `com.ivy.frp.*`, `com.ivy.domain.legacy.ui.*`). Renaming a package to match the namespace is a source-wide refactor, not a tidy-up.
- `temp.old-design` is a direct dependency: the legacy UI imports `com.ivy.design.*`, so `:temp:old-design` cannot be deleted while this module lives.
- No `src/main/res` of its own - resources come from the modules it depends on.
- `opencsv`, `ktor` and `keval` are here only because of specific features (CSV import, sync, keval/security); removing a dependency means removing the feature that uses it.

## Side Effects
- Room schema/database wiring (via the `ivy.room` plugin).
- WorkManager workers (bill/transaction reminders, transfer sync).
- DataStore preferences file `ivy_wallet`.
- Network: ktor client for the PocketBase sync server.
- Notification posting and channel creation.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

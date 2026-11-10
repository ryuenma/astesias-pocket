# App Migrations

## Overview
Tiny one-off migration framework for the app module: a `Migration` contract plus a manager that runs
each registered migration at most once per install, tracking completion in the shared Preferences
DataStore.

## Interface
- `interface Migration { val key: String; suspend fun migrate() }`
- `class MigrationsManager @Inject constructor(@ApplicationContext context: Context, disableGitHubAutoBackupMigration: Lazy<DisableGitHubAutoBackupMigration>)`
  - `suspend fun executeMigrations()`
- Subdirectories: `./impl/EGM.md`

## Invariants
- Completion flag is the datastore boolean `migration_<key>`; a migration whose key changes runs again
  for every existing install.
- `executeMigrations()` delays 2 s before touching anything, so migrations never block app startup.
- Failures are swallowed (`catch { e.printStackTrace() }`) **and the flag is still set** — a migration
  that throws will not be retried. This is the framework's most important (and least forgiving)
  property.
- Implementations are injected as `Lazy` and only materialised inside the migration loop.
- The migration list is the single source of truth: adding a migration means adding it to the list.

## Side Effects
- Reads and writes the shared Preferences DataStore (`context.dataStore`).
- Runs each migration body on `Dispatchers.IO`.
- Logs progress through Timber (`[MIGRATION] Executing/Finished ...`).
- Delays 2 s of wall-clock time on the caller's coroutine.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`

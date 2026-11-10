# Migration Implementations

## Overview
Concrete one-off data migrations run by `MigrationsManager`. Each entry is a small, idempotent-ish
datastore/WorkManager fix that removes state left behind by a removed feature.

## Interface
- `class DisableGitHubAutoBackupMigration @Inject constructor(@ApplicationContext context: Context) : Migration`
  - `override val key: String = "disable_github_auto_backups"`
  - `override suspend fun migrate()`

## Invariants
- `key` is the datastore marker suffix (`migration_<key>`), so it must never change once released —
  changing it would re-run the migration for existing installs.
- Cancels the unique work `GITHUB_AUTO_BACKUP_WORK` before clearing preferences, so no backup worker
  can restart with credentials that are about to disappear.
- Removes exactly four datastore keys: `GITHUB_PAT`, `GITHUB_REPO`, `GITHUB_OWNER`,
  `GITHUB_LAST_BACKUP_EPOCH_SEC`. Any other key is left untouched.
- Registered in `MigrationsManager`; a class not listed there never runs.

## Side Effects
- Cancels WorkManager unique work for the app.
- Mutates the shared Preferences DataStore (`context.dataStore.edit`), removing the GitHub backup
  credentials and last-backup timestamp.
- Runs on `Dispatchers.IO` (the caller wraps it).

## Verification
- Run: `./gradlew :app:testDebugUnitTest`

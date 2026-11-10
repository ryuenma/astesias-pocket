# com.ivy.data.datastore — preference store

## Overview

The AndroidX DataStore preferences instance and its key catalogue. This is the modern replacement
for `com.ivy.base.legacy.SharedPrefs`; feature flags in `com.ivy.domain.features` read through it.

## Interface

- `typealias IvyDataStore = DataStore<Preferences>`
- `val Context.dataStore: IvyDataStore` — `preferencesDataStore(name = "ivy_wallet_datastore_v1")`.
- `@Composable fun datastore(): IvyDataStore` — resolves via `LocalContext`.
- `object DatastoreKeys` — `GITHUB_OWNER`, `GITHUB_REPO`, `GITHUB_PAT`, `GITHUB_LAST_BACKUP_EPOCH_SEC`
  (all `@Deprecated("will be removed")`), and `fun ivyFeature(key: String): Preferences.Key<Boolean>`
  producing `"feature_$key"`.

## Invariants

- The store name `"ivy_wallet_datastore_v1"` is a **persistence identifier**. Renaming it orphans
  every stored preference.
- `ivyFeature(key)` is the only supported way to build a feature-flag key: it prefixes `"feature_"`.
  A hand-built `booleanPreferencesKey` with the same intent silently reads a different value.
- The four deprecated GitHub keys must stay until a migration removes the data; deleting the
  constants would orphan whatever is stored under them.

## Side Effects

- Reads/writes the `ivy_wallet_datastore_v1` preferences file in the app's data directory.
- `datastore()` requires a `Context` from the composition, so it cannot be used outside Compose.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

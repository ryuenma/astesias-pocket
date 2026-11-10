# IvyDataStore

## Overview
The inherited DataStore wrapper over the `ivy_wallet` preferences file.

## Interface
- `IvyDataStore` (`@Singleton`, `@Inject`) - `insert(pair)`, `insert(key, value)`, `remove(key)`, `get(key)`.
- The backing `Context.dataStore` delegate is declared with `preferencesDataStore(name = "ivy_wallet")`.

## Invariants
- The preference file name `ivy_wallet` is the persisted identity of this store: renaming it orphans every setting already written on installed devices.
- Class, delegate and every method are `@Deprecated("legacy - use IvyWalletDataStore instead")` - new code must use the replacement store, but existing settings still live here.
- The `preferencesDataStore` delegate must be declared once per file name; a second declaration for `ivy_wallet` in this module would break the single-instance guarantee.

## Side Effects
- Writes and reads the `ivy_wallet` preferences file on disk (suspend, via `DataStore.edit`/`data`).

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

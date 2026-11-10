# com.ivy.domain.features — feature flags

## Overview

Typed boolean feature flags backed by the app's DataStore. Each flag is a `BoolFeature` identified by a
string key, and `Features` is the catalogue the rest of the app injects.

## Interface

- `interface Features` — `sortCategoriesAscending`, `compactAccountsMode`, `compactCategoriesMode`,
  `showTitleSuggestions`, `showCategorySearchBar`, `hideTotalBalance`, `showDecimalNumber`,
  `standardKeypadLayout`, `showAccountColorsInTransactions`, and `val allFeatures: List<BoolFeature>`.
- `@Immutable class BoolFeature(key: String, group: FeatureGroup? = null, name: String? = null,
  description: String? = null, private val defaultValue: Boolean)`
  - `@Composable fun asEnabledState(): Boolean`
  - `suspend fun isEnabled(appContext: Context): Boolean`
  - `fun enabledFlow(appContext: Context): Flow<Boolean?>`
  - `suspend fun set(appContext: Context, enabled: Boolean)`
- `enum class FeatureGroup { Category, Account, Other }`
- `class IvyFeatures @Inject constructor(...) : Features` — the concrete catalogue.

## Invariants

- **The `key` string is persisted state.** It is stored as `feature_<key>` via
  `DatastoreKeys.ivyFeature`; renaming a key silently resets that flag to its default for every install.
- `defaultValue` is private and is the fallback in **both** `isEnabled` and `enabledFlow`, so a flag that
  has never been written reports its default rather than `null`.
- `enabledFlow` returns `Flow<Boolean?>` while `isEnabled` returns non-null `Boolean`: the nullable
  variant exists only for `collectAsState(defaultValue)`. A caller that collects the flow directly must
  handle null.
- `BoolFeature` is `@Immutable` and carries a `Context`-parameterised API on purpose — flags must not
  capture an application `Context` in the instance, or the singleton graph would hold an Activity.
- `allFeatures` must list every flag declared above; a flag omitted from the list is invisible to any
  settings screen that iterates it while still being readable by code that injects it.
- `FeatureGroup` is presentation grouping only and must not be used to decide behaviour.

## Side Effects

- `isEnabled` / `set` read and write the `ivy_wallet_datastore_v1` DataStore (`feature_<key>` booleans).
- `asEnabledState` subscribes a `collectAsState` to that DataStore inside composition, so a flag write
  anywhere recomposes every screen reading it.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

# com.ivy.data.datasource — local data sources

## Overview

Local (on-device) data sources that are not Room DAOs. Currently just the legal-disclaimer flag,
which is deliberately not in the Room database because it must be readable before onboarding.

## Interface

- `class LocalLegalDataSource @Inject constructor(DataStore<Preferences>)`
  - `suspend fun getIsDisclaimerAccepted(): Boolean?`
  - `suspend fun setDisclaimerAccepted(accepted: Boolean)`
  - private `DisclaimerAcceptedKey = booleanPreferencesKey("disclaimer_accepted")`.

## Invariants

- `getIsDisclaimerAccepted` returns `Boolean?` and is `null` when the key was never written. `null`
  means "never answered", which is **not** the same as `false` ("declined"): a consumer that coerces
  null to false will re-prompt a user who has not been asked yet, and one that coerces to true will
  skip the disclaimer on a fresh install.
- The `"disclaimer_accepted"` preference key is persisted state; rename it only with a migration.

## Side Effects

- Reads and writes the `ivy_wallet_datastore_v1` preferences file.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

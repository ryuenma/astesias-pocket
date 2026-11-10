# Legacy View State Types

## Overview
Small immutable carriers passed between the legacy ViewModels and screens.

## Interface
- `AppBaseData(...)` - base app data exposed to the root UI.
- `BufferInfo(...)` - buffer amount and how much of it is left.
- `LegacyDueSection(...)` and `DueSection(...)` - the due/upcoming list section; both deprecated (one for the legacy transaction type, one as "Legacy data model").
- `EditTransactionDisplayLoan(...)` - the loan display data shown inside the edit-transaction screen.

- [datastore](./datastore/EGM.md) - `IvyDataStore`, the deprecated DataStore wrapper over the `ivy_wallet` preferences file.
- [model](./model/EGM.md) - Legacy time-range and UI model types (`TimePeriod`, `FromToTimeRange`, `LastNTimeRange`, `Month`, `MainTab`, `AccountData`).

## Invariants
- All are `@Immutable`, so they are safe to hand to Compose as snapshot state.
- `DueSection` and `LegacyDueSection` are different types, not aliases: a screen migrated to the shared model must switch types rather than cast.

## Side Effects
- None; data carriers only.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

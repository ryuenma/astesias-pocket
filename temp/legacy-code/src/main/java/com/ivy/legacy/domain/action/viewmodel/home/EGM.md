# Home Screen Actions

## Overview
The actions the legacy home screen binds to: what is due or overdue, whether to show balances and income, and the cache refreshes that keep the screen's derived state current.

## Interface
- `DueTrnsInfoAct`, `OverdueAct`, `UpcomingAct` - due/overdue/upcoming transaction information.
- `HasTrnsAct` - whether any transactions exist (first-run vs populated).
- `ShouldHideBalanceAct`, `ShouldHideIncomeAct` - privacy toggles for the two amount displays.
- `UpdateAccCacheAct`, `UpdateCategoriesCacheAct` - cache refresh actions.

## Invariants
- `ShouldHideBalanceAct`/`ShouldHideIncomeAct` are read-only queries over settings; the toggle itself is written elsewhere (`domain/action/settings`).
- The `Update*CacheAct` actions are side-effecting by design - they mutate a cache the UI reads, unlike every read action in this package.
- Each class is an `@Inject constructor` action with an `Input`/`Output` contract; callers go through `invoke`, not the constructor fields.

## Side Effects
- `UpdateAccCacheAct` and `UpdateCategoriesCacheAct` write to in-memory/derived caches read by the home screen.
- The read actions only read repositories/settings.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

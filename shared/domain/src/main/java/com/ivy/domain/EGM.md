# com.ivy.domain — domain root package

## Overview

Root of the domain module. Holds the two ports the app implements and groups the four domain
subpackages.

## Interface

- `interface AppStarter` — `fun getRootIntent(): Intent`, `fun defaultStart()`,
  `fun addTransactionStart(type: TransactionType)`.
- `interface RootScreen` — `val isDebug: Boolean`, `val buildVersionName: String`,
  `val buildVersionCode: Int`, `fun openUrlInBrowser(url: String)`, `fun shareCSVFile(fileUri: Uri)`,
  `fun shareFile(fileUri: Uri, mimeType: String)`, `fun shareZipFile(fileUri: Uri)`,
  `fun openGooglePlayAppPage(appId: String)`.

## Subdirectories & Boundaries

- [di/](./di/EGM.md) — Domain Hilt module binding IvyFeatures to Features, plus the FeaturesEntryPoint used by non-injectable call sites.
- [features/](./features/EGM.md) — Typed boolean feature flags: the Features catalogue, BoolFeature (DataStore-backed, default-valued, Compose-aware) and FeatureGroup presentation grouping.
- [model/](./model/EGM.md) — Domain read models: StatSummary (per-currency transaction totals) and the inclusive TimeRange sealed interface.
- [usecase/](./usecase/EGM.md) — Use cases grouped by subject (account, android, billreminder, category, csv, exchange, transfer, wallet, xlsx) plus the mutable BalanceBuilder and StatSummaryBuilder accumulators.

## Invariants

- `AppStarter` starts the **RootActivity without knowing about it** — the app supplies the `Intent`.
  A use case that needs to launch UI must go through this port, never through a `Context` cast.
- `RootScreen` carries `BuildConfig` values (`isDebug`, `buildVersionName`, `buildVersionCode`) as
  **properties**, i.e. they are resolved once per implementation. A consumer that expects live build
  metadata must re-inject rather than cache the `RootScreen`.
- `shareFile(fileUri, mimeType)` exists because sharing an `.xlsx` as `text/csv` makes some apps open it
  as broken text; the MIME type must travel with the file. Do not "simplify" the CSV/XLSX/ZIP share
  functions into one that guesses.
- Both ports are interfaces with no default implementation in this module: if the app does not bind
  them, Hilt fails at compile time rather than at runtime.

## Side Effects

- Implementations start activities, open browsers, share files through the platform chooser and open
  the Play Store. The interfaces themselves have no effects.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

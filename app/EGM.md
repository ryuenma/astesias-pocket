# app — Astesia's Pocket application module

## Overview
The Android application module: `com.android.application` with `applicationId = "astesias.pocket"` and
`namespace = "com.ivy.wallet"`. It is the composition root — Hilt graph, single activity, Compose entry
point and nav graph — and it aggregates the `:feature:*`, `:shared:*` and `:temp:*` modules into one APK.

Build types are `debug` (`.debug` suffix, not minified, debug keystore), `demo` (minified, debug-signed,
`.debug` suffix — used by the Compose stability CI job) and `release` (minified, release keystore from
`SIGNING_*` env vars).

## Interface
- Gradle project `:app`; plugins: `com.android.application`, `kotlin-android`, Compose compiler,
  `dagger.hilt.android.plugin`, kotlinx-serialization, KSP, detekt
- `applicationId = "astesias.pocket"`, `namespace = "com.ivy.wallet"`; `compileSdk`/`targetSdk`/`minSdk`
  and version come from `gradle/libs.versions.toml`
- Depends on every feature module (`attributions`, `balance`, `budgets`, `categories`,
  `editTransaction`, `features`, `home`, `importData`, `loans`, `main`, `onboarding`, `piechart`,
  `plannedPayments`, `reports`, `search`, `settings`, `transactions`), on `shared:base`,
  `shared:data:core`, `shared:domain`, `shared:ui:core`, `shared:ui:navigation`, `temp:legacyCode`,
  `temp:oldDesign`
- Build config: `buildFeatures { compose = true; buildConfig = true }`, lint baseline
  `lint-baseline.xml`, reports under `build/reports/lint/`
- `proguard-rules.pro` — keep rules for the release/demo minified builds
- Contracts: `./src/EGM.md`

## Invariants
- `google-services` and `firebase-crashlytics` plugins are intentionally **not** applied and
  `google-services.json` is deleted: applying them would bind this fork to Ivy's Firebase project and
  ship crash reports under `com.ivy.wallet`. Crashlytics-shaped helpers log through Timber instead.
- Release signing reads `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` from the
  environment; the debug keystore credentials are hard-coded in this file.
- `demo` and `release` share `proguard-rules.pro` and `matchingFallbacks.add("release")`; only debug is
  debuggable.
- Lint is configured with `abortOnError = false` and a baseline — lint never fails the build by design.
- `androidResources { generateLocaleConfig = true }` — the locale config is generated, not committed.
- Every module this module depends on is constrained by the `buildSrc` convention plugins
  (`ivy.module`, `ivy.compose`, `ivy.feature`, …): changing a convention plugin changes how `:app`
  compiles.

## Side Effects
- Assembles an APK/Bundle signed with a checked-in debug keystore (`../debug.jks`) or the release
  keystore (`../sign.jks`); no network publishing from Gradle.
- Lint writes `build/reports/lint/lint.html` and `lint.xml`, and can rewrite `lint-baseline.xml` when
  the baseline task is used (`scripts/lintBaseline.sh`).
- KSP generates Hilt/Room sources under `build/generated/`.
- Exposes an installable app whose process-level side effects are documented in `./src/EGM.md`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
- Assemble: `./gradlew :app:assembleDemo` (what `scripts/composeStability.sh` uses)

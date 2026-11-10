# app — main source set

## Overview
Main variant of the app module: the Kotlin sources, the resources and the manifest. Note the manifest
still declares `android:name=".IvyAndroidApp"` and `package="com.ivy.wallet"` because the module
`namespace` in `build.gradle.kts` is `com.ivy.wallet`, even though the `applicationId` is
`astesias.pocket`.

## Subdirectories & Boundaries
- `java`: `./java/EGM.md` — Kotlin/Java sources (`com.ivy`, `com.ivy.wallet`)
- `res`, `mipmap-*`, `values`, `xml`: launcher icons, strings and shortcuts (`@xml/shortcuts`); resources only, no contract

## Invariants
- `AndroidManifest.xml` is the authority for exported components: `RootActivity` is the launcher (also
  handles `ivy.wallet.intent.action.add_transaction`) and `PaymentTileService` is the exported
  `QS_TILE` service.
- The manifest removes `androidx.startup.InitializationProvider` (`tools:node="remove"`) to fix
  HiltWorker injection for newer WorkManager — do not re-add it.
- `android:allowBackup="false"` and `usesCleartextTraffic="false"` are set here; the `debug` variant
  overrides cleartext for a local PocketBase (`app/src/debug/AndroidManifest.xml`).
- Packaged permissions are fixed by this manifest (INTERNET, notifications, boot-completed, alarm,
  vibrate, keyguard, wake lock, legacy storage ≤28); a new capability needs a manifest edit here.

## Side Effects
- None at contract level; the declared permissions and exported components are what the OS enforces.

## Verification
- Run: `./gradlew :app:assembleDemo`

# App Lock Screen

## Overview
Full-screen Compose lock UI shown by `RootActivity` whenever `RootViewModel.appLocked` is `true`.
It is deliberately minimal: a lock badge, a fingerprint illustration and the unlock callbacks — it
holds no state and performs no authentication itself.

## Interface
- `@Composable fun BoxWithConstraintsScope.AppLockedScreen(onShowOSBiometricsModal: () -> Unit, onContinueWithoutAuthentication: () -> Unit)`
- Imports the app theme tokens from `com.ivy.legacy` (`IvyWalletPreview`) and `com.ivy.wallet.ui.theme` (`Gray`, `White`, `IvyButton`)

## Invariants
- Extends `BoxWithConstraintsScope`, so it can only be called inside `IvyUI { }` (which supplies that
  scope) — that is why `RootActivity` wraps it in `IvyUI`.
- Stateless: it never reads or writes the lock flag; `RootViewModel` owns `appLocked`.
- When the app-lock preference is off the screen is never composed at all (the caller gates on
  `appLocked == true`).
- Both callbacks are provided by the caller; the "continue without authentication" path unlocks the app
  without biometrics, so the screen is not a security boundary on its own.

## Side Effects
- None directly. Taps only invoke the supplied lambdas; the biometric prompt and the unlock are
  performed by `RootActivity`/`RootViewModel`.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`

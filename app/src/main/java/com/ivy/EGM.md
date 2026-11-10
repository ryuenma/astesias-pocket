# com.ivy — navigation graph

## Overview
The package that holds the single Compose navigation switch for the whole app. It is the one place
where navigation `Screen` objects are resolved to feature Composables, so it is the largest fan-out
point from `:app` into the `:feature:*` modules.

## Interface
- `@Composable fun BoxWithConstraintsScope.IvyNavGraph(screen: Screen?)` — a `when (screen)` over every `Screen` in `:shared:ui:navigation`

## Invariants
- `screen == null` renders nothing (the app is still loading; see `RootViewModel.appLocked`).
- Every `Screen` subclass must have exactly one branch; the `when` has no `else`, so adding a `Screen`
  breaks compilation until it is routed here — that is the intended safety net.
- Several screens are aliased on import (`CSVScreenComposable`, `ImportCSVScreenComposable`,
  `TransferAnswersScreenImpl`, `PendingTransfersScreenImpl`) because the navigation model and the
  feature composable share a name — keep the aliases when editing.
- `SettingsScreen`, `FeaturesScreen` and `AttributionsScreen` take no argument or the object itself; they
  are the object-typed screens.
- Annotated `@ExperimentalFoundationApi`, `@ExperimentalAnimationApi` and extends `BoxWithConstraintsScope`,
  so it must stay inside `IvyUI { }`.

## Side Effects
- None beyond composition: it delegates to feature composables, which own their own effects.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`

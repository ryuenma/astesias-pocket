# com.ivy.ui.di — UI Hilt bindings

## Overview

Binds the UI module's interface/implementation pairs into the singleton graph. Three bindings, all
`@InstallIn(SingletonComponent::class)`.

## Interface

- `interface IvyUiBindings` — `@Binds` for:
  - `IvyTimeFormatter` → `TimeFormatter`
  - `AndroidDevicePreferences` → `DevicePreferences`
  - `AndroidDateTimePicker` → `DateTimePicker`

## Invariants

- `DateTimePicker` is `@Stable` but **not** a singleton-scoped component: it is bound here only so
  the Compose layer can inject it. It must stay stateless — a binding here does not give it a scope.
- Exactly one binding per interface. Adding a fake binding for tests breaks compilation of the app
  graph (duplicate binding), not just the test.
- `IvyUiBindings` imports the impls from `com.ivy.ui.time.impl`, so a renamed impl requires editing
  this file — that coupling is intentional and keeps impls out of consumer imports.

## Side Effects

- Extends the singleton component at app startup (Hilt graph construction).

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`

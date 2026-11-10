# shared/ui/core — design system and shared UI

## Overview

Android library module (namespace `com.ivy.ui`) containing the app's Material 3 theme, the color
palette, shared composables, the money formatter, and the time-formatting/picker stack. It is the
only module allowed to define visual tokens; feature screens consume them.

`build.gradle.kts`: `implementation(projects.shared.base)`, `implementation(projects.shared.domain)`
and the `ivy.feature` convention plugin (which brings Compose, Paparazzi and Hilt).

## Interface

- `build.gradle.kts` — the dependency surface above; `android.namespace = "com.ivy.ui"`.
- `src/` → [./src/EGM.md](./src/EGM.md)

## Subdirectories & Boundaries

- [src/](./src/EGM.md) — Source-set container for :shared:ui:core (main production + JVM/Paparazzi tests).

## Invariants

- `shared/ui/core` depends on `domain` **directly** (not only through `base`), because
  `FormatMoneyUseCase` reads `Features`. Removing that dependency breaks the money formatter.
- It must never depend on `shared/ui/navigation`: navigation depends on this module, so the reverse
  edge is a Gradle cycle.
- Paparazzi is applied to this module: any composable added here becomes screenshot-testable, and a
  UI change without a snapshot update fails `verifyPaparazziDebug`.

## Side Effects

- Inherited from subpackages: Compose recomposition, `UriHandler` launches (open-source card),
  `Toast`-free but UI-thread work.
- Paparazzi writes snapshot images under `src/test/snapshots/`.

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`
- Screenshot gate: `./gradlew :shared:ui:core:verifyPaparazziDebug`

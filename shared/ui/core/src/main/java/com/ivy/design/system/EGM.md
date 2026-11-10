# com.ivy.design.system — Material 3 theme

## Overview

Maps the Ivy palette onto a Material 3 `ColorScheme` and exposes the single theme wrapper every
screen uses. Light and dark schemes are constructed explicitly rather than derived from a seed, so
brand colors survive Material 3 defaults.

## Interface

- `@Composable fun IvyMaterial3Theme(isTrueBlack: Boolean, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`
- `private fun ivyLightColorScheme(): ColorScheme`
- `private fun ivyDarkColorScheme(isTrueBlack: Boolean): ColorScheme`

## Subdirectories & Boundaries

- [colors/](./colors/EGM.md) — Color tokens: ColorShades ramp and the @Immutable IvyColors palette (neutrals + 7 hues x 7 shades, incl.

## Invariants

- `isTrueBlack` is a **required** parameter with no default: AMOLED mode is a user setting, and
  defaulting it would silently disable the feature. In dark mode it selects
  `IvyColors.TrueBlack` (`#000000`) over `IvyColors.Black` for `background`/`surface`, and flips
  `inverseOnSurface` accordingly.
- Both schemes must define the same fields; a missing field falls back to Material's default purple
  and breaks brand consistency invisibly.
- `dark` defaults to `isSystemInDarkTheme()` but callers (e.g. `IvyPreview`) override it — do not
  read the system theme inside components.

## Side Effects

- Provides `MaterialTheme` (composition local) to its content subtree; nothing outside Compose.

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`

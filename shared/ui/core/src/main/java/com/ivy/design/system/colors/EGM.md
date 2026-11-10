# com.ivy.design.system.colors — palette

## Overview

The raw color tokens: a neutral ramp, seven brand hues each with seven shades, and the two special
blacks used for AMOLED. Everything else in the app is expected to pick colors from here or from
`MaterialTheme.colorScheme`, never from a literal.

## Interface

- `data class ColorShades(extraLight, light, kindaLight, primary, kindaDark, dark, extraDark: Color)`
- `@Immutable object IvyColors` — neutrals `White`, `ExtraLightGray`, `LightGray`, `Gray`,
  `DarkGray`, `ExtraDarkGray`, `Black`, `TrueBlack`; hues `Red`, `Orange`, `Yellow`, `Green`,
  `Blue`, `Purple`, `Pink`, each a `ColorShades`.

## Invariants

- `ColorShades` has exactly seven named steps and they are **not** a linear scale:
  `kindaLight`/`kindaDark` sit between `light`/`primary` and `primary`/`dark`. Code that assumes
  `primary` is the midpoint of the ramp picks the wrong contrast.
- `IvyColors` is `@Immutable` and all members are `val`: it is a compile-time constant table, and
  every shade is a literal ARGB value. Changing a literal is a visual change that must be
  accompanied by a Paparazzi snapshot update.
- `White` is `#FAFAFC`, not `#FFFFFF`; only `TrueBlack` is a pure literal extreme
  (`#000000`), which is what AMOLED mode relies on.

## Side Effects

- None (pure constants).

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`

# Level 0 Design Tokens

## Overview
The token layer: the flat colour palette and colour maths, the typography and shape interfaces, and the theme provider that installs them into composition locals.

## Interface
- `Colors.kt` - the palette (`White`, `Black`, `Ivy`, `Purple`, `Purple1`, `Purple2`, `Blue`, `Blue2`, `Blue3`, `Green`, `Green2`, `Green3`, `Green4`, `Yellow`, `Orange`, `Orange2`, `Orange3`, `Red`, `Red2`, `Red3`, plus each `*Light`/`*Dark`, `TrueBlack`, `MediumBlack`, `Gray`, `MediumWhite`, `Transparent`), `Gradient`, `GradientGreen`, and helpers `findContrastTextColor`, `isDarkColor` (2 overloads), `Color.asBrush()`, `dynamicContrast()`, `lighten()`, `darken()`, `toHSVSpec()`, `HSVSpec`.
- `interface IvyColors`, `interface IvyTypography`, `abstract class IvyShapes` - the token contracts a design system implements.
- `IvyTheme(...)`, `LocalIvyColors`, `LocalIvyTypography`, `LocalIvyShapes`, and `object UI` - the provider and the accessor legacy code reads tokens through (`UI.colors`, `UI.typography`, `UI.shapes`).
- `TypographyExt.kt` - `TextStyle.colorAs(color)`, `TextStyle.style(...)`.

## Invariants
- `Ivy` and `Purple` are both `0xFF6B4DFF` here; `com.ivy.wallet.ui.theme.IvyColors` (in `:temp:legacy-code`) declares the same values, so the two palettes must be changed together or the two UI generations drift apart.
- `UI` is an object reading the composition locals, so it is only valid inside `IvyTheme`. Reading `UI.colors` in a preview or a non-Compose context yields the unset local.
- `isDarkColor` has two overloads (`Color` and `@ColorInt Int`) - picking the wrong one inverts light/dark decisions without a compile error.
- The interfaces are implemented by the design systems in `../api/systems`, not here; this level must not depend on `l1`/`l2`.
- Everything is deprecated (old design system) except where noted per file.

## Side Effects
- `Colors.kt` is 323 lines - this contract covers its first 300 lines.
- Pure token access; no persistence, no I/O.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

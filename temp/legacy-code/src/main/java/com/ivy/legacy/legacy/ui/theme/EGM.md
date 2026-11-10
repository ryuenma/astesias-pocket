# Legacy Theme Colours

## Overview
`IvyColors.kt` in package `com.ivy.wallet.ui.theme`: the flat colour palette, gradients and colour maths (`findContrastTextColor`, `isDarkColor`, `dynamicContrast`, `lighten`, `darken`, `toHSVSpec`, `hsv`) that the legacy screens reference directly. It duplicates, and has drifted from, `:temp:old-design`'s token set - both exist because different screens import different copies.

## Interface
- Colour constants: `White`, `Black`, `Ivy`, `Blue`, `Green`, `Orange`, `Red`, `Red3` and their `*Light`/`*Dark` variants, `MediumBlack`, `Gray`, `MediumWhite`, `Transparent`.
- `Purple`, `GradientPurple` - the only two in the file that are **not** deprecated; the rest carry `@Deprecated("Old design system...")`.
- `Gradient` data class and presets `GradientRed`, `GradientGreen`, `GradientOrangeRevert`, `GradientIvy`.
- `Modifier.gradientCutBackgroundTop/Bottom(...)`, `pureBlur()`, `mediumBlur()`, `gradientExpenses()` - `@Composable` helpers returning `Modifier`/`Gradient`.
- `HSVSpec`, `Color.toHSVSpec()`, `Color.hsv(...)`, `Int.toComposeColor()` - colour-space conversions.

- [components](./components/EGM.md) - The legacy Compose component set: buttons, text fields, batteries, icon picker, currency picker, reorder modal.
- [modal](./modal/EGM.md) - Legacy bottom-sheet modals: `IvyModal`, delete/name/progress modals, loan, recurring rule and sync-server modals.
- [wallet](./wallet/EGM.md) - Wallet amount typography rows (`AmountCurrency*`) and the period selector.

## Invariants
- `Ivy` and `Purple` are both `0xFF6B4DFF`; in `:temp:old-design`'s `Colors.kt` they are the same value too, so swapping palettes by name must keep them in step.
- `isDarkColor` exists in two overloads (`Color` and `@ColorInt Int`); callers must pick the one matching their input or light/dark detection silently inverts.
- Everything except `Purple`/`GradientPurple` is deprecated; new code must use `:ivy-design` tokens instead of these constants.

## Side Effects
- Read-only Compose state: `pureBlur`, `mediumBlur` and `gradientExpenses` read `UI.colors`, so they must be called inside a theme scope.
- No persistence or I/O.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

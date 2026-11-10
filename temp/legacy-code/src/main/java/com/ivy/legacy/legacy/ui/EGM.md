# Legacy UI Package Root

## Overview
Package `com.ivy.domain.legacy.ui`: the legacy colour picker composable plus the `theme` tree below it. The package name says `domain` and `legacy` twice over, while the directory path says `legacy/ui` - so neither the path nor the package alone identifies this code.

## Interface
- `ColumnScope.IvyColorPicker(...)` - the picker; offers the free/premium palettes and reports the chosen colour.
- `private data class IvyColor` and `private ColorItem(...)` - the picker's internal colour model and swatch.
- `theme` - the legacy theme/component/modal tree.

- [theme](./theme/EGM.md) - `com.ivy.wallet.ui.theme` colours, gradients and colour maths used by every legacy screen.

## Invariants
- The picker is a `ColumnScope` extension - it only compiles inside a `Column`, which is how its swatch grid is laid out.
- The colour lists come from `com.ivy.design`'s `IVY_COLOR_PICKER_COLORS_FREE`/`IVY_COLOR_PICKER_COLORS_PREMIUM` (in `:temp:old-design`); a colour that is in neither list cannot be chosen, and one added to a list appears without any change here.
- `IvyColor` is private: callers deal in `Color`, so changing the internal model is not a public API change.
- The whole file is annotated `@Deprecated("Old design system. Use `:ivy-design` and Material3")`.

## Side Effects
- Pure rendering; the selection is reported through the callback.
- No storage: the chosen colour is persisted by the caller (category/account write path).

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

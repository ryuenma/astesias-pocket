# Design System Root

## Overview
Package `com.ivy.design`: the `IvyContext` base type every legacy screen resolves through, and the colour-picker palettes.

## Interface
- `abstract class IvyContext` - `@Deprecated("Legacy code. Don't use it, please.")`; the app's `IvyWalletCtx` (in `:temp:legacy-code`) extends it.
- `IVY_COLOR_PICKER_COLORS_FREE`, `IVY_COLOR_PICKER_COLORS_PREMIUM` (`IvyColorPicker.kt`) - the two palette lists the colour picker offers.

- [api](./api/EGM.md) - Design-system API: the `IvyDesign` contract and `IvyUI` composition locals.
- [l0_system](./l0_system/EGM.md) - Level 0 tokens: colours, typography, shapes, theme provider.
- [l1_buildingBlocks](./l1_buildingBlocks/EGM.md) - Level 1 primitives: dividers, icons, text, shape containers, spacers.
- [l2_components](./l2_components/EGM.md) - Level 2 components: button, button-with-icon, checkbox, icon button, switch.
- [utils](./utils/EGM.md) - Design-system utilities: insets, keyboard, padding, previews, animation, Compose and View helpers.

## Invariants
- `IvyContext` is deprecated but is the type of `LocalIvyContext`; removing it means changing every legacy composable, not just this file.
- The picker palettes are hardcoded lists: a colour added to one list but not the other is either free or premium depending only on which list it was appended to.

## Side Effects
- None: types and constants.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

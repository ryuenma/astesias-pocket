# Level 1 Building Blocks

## Overview
The primitives level 2 components are assembled from: dividers, icons, text, shape containers and spacers. All operate directly on `IvyColors`/`IvyTypography` and take no dependency on level 2.

## Interface
- Dividers (`Dividers.kt`): `DividerH`, `DividerV`, `RowScope.DividerW`, `Divider`, and `sealed class DividerSize`.
- Icons (`IvyIcon.kt`): `IvyIcon`, `IvyIconScaled`, `enum class IconScale`.
- Text (`IvyText.kt`): `IvyText`.
- Shape containers (`Shapes.kt`): `Shape`, `ShapeOutlined`.
- Spacers (`Spacers.kt`): `SpacerVer`, `SpacerHor`, `RowScope.SpacerWeight`.

- [data](./data/EGM.md) - `Background` sealed type and `IvyPadding` value type, with their `Modifier` extensions.

## Invariants
- `DividerW` and `SpacerWeight` are `RowScope` extensions - they only compile inside a `Row`, which is how weight-based spacing is enforced.
- `DividerSize` is sealed; a `Divider` call with a new size is an exhaustive-`when` change for every consumer.
- Every declaration is `@Deprecated("Old design system. Use `:ivy-design` and Material3")`.
- This level must not import from `../l2_components` - the layering (l0 tokens → l1 primitives → l2 components) is what keeps the tokens replaceable.

## Side Effects
- Pure rendering; no state, no I/O.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

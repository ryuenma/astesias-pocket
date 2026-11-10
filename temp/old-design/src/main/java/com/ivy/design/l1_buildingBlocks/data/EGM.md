# Level 1 Data Types

## Overview
`Background` and `IvyPadding`: the two value types the level 1 primitives take as parameters, each with the `Modifier` extensions that apply it.

## Interface
- `sealed class Background` with `Modifier.background(background)` and `Modifier.clipBackground(background)`.
- `data class IvyPadding(...)` - consumed by `Modifier.ivyPadding(ivyPadding)` in `../utils`.

## Invariants
- `Background` is sealed, so a new variant is a source-visible change for every `when` over it.
- `background` and `clipBackground` differ in whether the shape clips the child: using `background` where a child overflows draws outside the intended shape.
- Both types are deprecated with the rest of the old design system.

## Side Effects
- None: value types and `Modifier` factories.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

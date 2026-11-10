# Level 2 Components

## Overview
The first real components of the design system: a button family, checkbox, icon button and switch. Each wraps the Material primitive and applies `IvyColors`/`IvyShapes`/`IvyTypography` from level 0.

## Interface
- `Button(...)` - declared **twice** in this package: the text-only overload in `Button.kt` and the icon overload in `ButtonWithIcon.kt`, together with `enum class Mode` (icon left/right, wrap/fill).
- `Checkbox(...)` and `CheckboxWithText(...)`.
- `IconButton(...)`.
- `Switch(...)`.

## Invariants
- The two `Button` overloads share a name and a package, so an import of `Button` resolves by argument types - adding a parameter to one can silently move a call site to the other.
- All components are `@Deprecated("Old design system. Use `:ivy-design` and Material3")`.
- Colour, shape and typography come from the composition locals in `../l0_system`; a component rendered outside `IvyTheme` gets the unset local and fails, so these are not usable standalone.

## Side Effects
- Pure rendering plus the callbacks they take; no I/O.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

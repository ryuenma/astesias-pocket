# Concrete Design System

## Overview
`AstesiaWalletDesign.kt` declares `abstract class IvyWalletDesign : IvyDesign` - the concrete binding of the token interfaces. `defaultDesign()` (in `../utils/Preview.kt`) and the legacy app both instantiate it.

## Interface
- `abstract class IvyWalletDesign : IvyDesign` - wires `IvyColors`, `IvyTypography` and `IvyShapes` into the `IvyDesign` contract.
- File name (`AstesiaWalletDesign.kt`) and class name (`IvyWalletDesign`) disagree - the fork renamed the file, not the type.

## Invariants
- The class is `abstract`, so previews and the app subclass it rather than instantiating `IvyDesign` directly; adding an unimplemented member to `IvyDesign` breaks every subclass at once.
- It is deprecated with the rest of the old design system.

## Side Effects
- None: it is a wiring type.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

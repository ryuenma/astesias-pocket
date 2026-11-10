# Design System API

## Overview
The contract the two UI generations meet on: `IvyDesign` (what a design system must provide) and `IvyUI` (the composable that installs it, plus the time-related composition locals).

## Interface
- `interface IvyDesign` (`IvyDesign.kt`) - the token provider contract.
- `val LocalIvyContext = compositionLocalOf<IvyContext>` - the app context every legacy composable resolves through `ivyContext()`.
- `LocalTimeConverter`, `LocalTimeProvider`, `LocalTimeFormatter` - time composition locals, each `@Deprecated("Used only for time migration...")` and `@Suppress("CompositionLocalAllowlist")`.
- `IvyUI(...)` - the root composable installing the design system, plus private `WrapWithSurface(...)` and `ivyContext()`.

- [systems](./systems/EGM.md) - `IvyWalletDesign`, the concrete design system wiring colours/typography/shapes.

## Invariants
- The three time locals are explicitly a migration bridge: new code takes `TimeProvider`/`TimeConverter` through DI, and these exist only so legacy composables keep compiling.
- `ivyContext()` returns the current `IvyContext` and throws if none was installed (no default value on the local) - every legacy composable transitively depends on `IvyUI` being above it.
- `LocalIvyContext` has no default, so a preview must supply one (`IvyComponentPreview` does).

## Side Effects
- `IvyUI` wraps the content in a `Surface`, so it affects background and elevation of everything below it.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

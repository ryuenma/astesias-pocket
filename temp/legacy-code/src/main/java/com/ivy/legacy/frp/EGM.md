# Functional-Reactive Core

## Overview
The vocabulary the legacy code was written in: source-retained purity annotations and the `Composition` primitives that let actions be composed without naming intermediate types.

## Interface
- `@Pure`, `@Total`, `@Partial(inCaseOf)`, `@SideEffect` (`Annotations.kt`) - all `SOURCE`-retained and `@MustBeDocumented`.
- `Composition.kt` (298L), `Composition2.kt`, `CompositionN.kt` - the composition primitives for one, two and N actions.
- `onScreenStart(...)` (`OnScreenStart.kt`) - `@Deprecated("Legacy code. Don't use it, please.")` `LaunchedEffect` wrapper.
- `Utils.kt` - small helpers for the above.

- [action](./action/EGM.md) - `Action`/`FPAction` base types and the `Composition` filter/map/sum combinators.
- [monad](./monad/EGM.md) - `Res` result monad (Error/Loading/Success) and its composition helpers.
- [viewmodel](./viewmodel/EGM.md) - ViewModel factory helper bridging compositions into `ViewModel`s.

## Invariants
- The annotations are documentation, not checks: `@Pure` compiles over a function that reads the clock. Treat a missing annotation as "unverified", never as "impure".
- `@Total` means defined for every input, `@Partial(inCaseOf)` names the case it is not - a `@Partial` function returning null is behaving correctly, not failing.
- `Composition.kt` (298L) is at the read cap; this contract covers its first 300 lines.

## Side Effects
- None in the annotations layer; `onScreenStart` schedules a coroutine on composition entry.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

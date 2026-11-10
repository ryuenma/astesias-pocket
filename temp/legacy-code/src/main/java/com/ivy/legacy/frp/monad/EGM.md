# Res Monad

## Overview
`Res`, the result type the pre-Arrow code uses to model loading/error/success, plus its composition helpers.

## Interface
- `Res<out E, out T>` - sealed class with `Error`, `Loading`, `Success`.
- `ResComposition.kt` - the `Composition`-based helpers that map/fold a `Res`.

## Invariants
- `@Deprecated("Legacy code. Use Arrow's Either")` - the type is kept only for the classes still returning it.
- `Res` carries loading as a state, so a `Res`-returning call is a UI-shaped result and not a domain result; using it in the domain layer conflates "in flight" with "failed".
- Composition over `Res` is not short-circuiting in the same way `Either` is - check the helper before assuming error propagation.

## Side Effects
- None.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

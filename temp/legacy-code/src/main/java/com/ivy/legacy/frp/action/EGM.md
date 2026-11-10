# FRP Actions & Composition

## Overview
The base action types and the `Composition` combinators that filter, map and sum them.

## Interface
- `abstract class Action<in I, out O>` and `abstract class FPAction<I, O> : Action<I, O>()` - the two action bases.
- `CompositionFilter.kt`, `CompositionMap.kt`, `CompositionSum.kt` - combinators over actions.

## Invariants
- `Action` is `@Deprecated("Legacy code. Don't use it, please.")`, yet every action in `domain/action` derives from it - the base type is frozen, not removable.
- `Composition` combinators take actions, not values, so each combinator adds one more layer of action composition; the sum combinator returns the per-input sums as a list, in input order.

## Side Effects
- None by themselves; they execute whatever action they wrap.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

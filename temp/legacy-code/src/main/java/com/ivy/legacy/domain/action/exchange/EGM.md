# Exchange Action

## Overview
The action wrapper around the pure exchange functions: resolves what rate to use, then converts.

## Interface
- `ExchangeAct(...)` - converts an amount between two currencies.
- `actInput(...)` - helper building the action input.

## Invariants
- The rate resolution is the impure part and lives here, not in `domain/pure/exchange`.
- A missing rate must surface as `None`/null from the action; substituting 1:1 here would corrupt every converted total.

## Side Effects
- Reads exchange rates from the repository (and whatever refresh policy that repository applies).

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

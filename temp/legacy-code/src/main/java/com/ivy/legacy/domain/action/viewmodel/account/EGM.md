# Account Screen Actions

## Overview
`AccountDataAct` assembles the display data for the account screen: the account plus what the screen needs to render it (balance/currency view state).

## Interface
- `AccountDataAct(...)` - the single action of this package.

## Invariants
- Assembles presentation data only; it must not write - mutations belong to the creators in `domain/deprecated/logic`.
- Output is screen-shaped (`AccountData` view state), not a domain entity, so reusing it outside the account screen means re-deriving truth.

## Side Effects
- Reads repositories (injected).

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

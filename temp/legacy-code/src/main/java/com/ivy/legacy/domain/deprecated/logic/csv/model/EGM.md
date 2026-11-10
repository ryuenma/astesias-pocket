# CSV Import Model

## Overview
The value types the CSV importer maps rows into.

## Interface
- `ImportType` - `@Immutable` enum of import sources; by owner decision it has **one** member (the app's own backup export) rather than nine unreachable third-party adapters.
- `RowMapping(...)` - one parsed row: the mapped fields ready for transaction creation.
- `JoinResult(...)` - the result of joining a row against existing data: `transaction`, `category`, and the original `csvAmount` (which may be negative).

## Invariants
- `ImportType` having a single member is intentional, not incomplete: adding a member without an adapter behind it would create a branch nothing can reach.
- `JoinResult.csvAmount` is the raw CSV value and is allowed to be negative even when the final `transaction` amount is not.
- `JoinResult.category` is a full category object because `Transaction` carries only `categoryId`.

## Side Effects
- None: these are data holders.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

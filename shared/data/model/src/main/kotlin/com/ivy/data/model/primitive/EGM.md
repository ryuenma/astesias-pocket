# com.ivy.data.model.primitive — validated value classes

## Overview

Make-illegal-states-unrepresentable primitives. Each is a `@JvmInline value class` with a
**private constructor** and a companion implementing `Exact`, so the only entry points are
`from(...): Either` and `unsafe(...)`.

## Interface

- Strings: `NotBlankTrimmedString(value)` — trims, rejects blank; `IconAsset(id)` — trims,
  lowercase, rejects spaces; `AssetCode(code)` — trims, uppercases, with constants `USD`, `EUR`, `GBP`.
- Doubles: `PositiveDouble` (`> 0`, finite), `NonNegativeDouble` (`>= 0`, finite), `NonZeroDouble` (`!= 0`, finite).
- Integers: `PositiveInt` (`> 0`), `NonNegativeInt` (`>= 0`, with `Zero`), `NonNegativeLong` (`>= 0`, with `Zero`).
- Ids/colors: `AssociationId(value: UUID) : UniqueId`, `ColorInt(value: Int)` (unvalidated by design).
- Widening helpers: `PositiveDouble.toNonNegative()`, `PositiveInt.toNonNegative()`, `Long.toNonNegative()`.

## Invariants

- **Private constructors are the whole point.** Adding a public constructor or a `copy`-style
  factory re-opens the invalid states the model was built to exclude.
- `Unsafe` (`unsafe(...)`) converts a validation failure into an `IllegalArgumentException`
  subclass. Use it only where the value is already proven valid (e.g. derived from a validated
  one), never on raw external input such as CSV import or network payloads.
- `PositiveDouble`/`NonNegativeDouble`/`NonZeroDouble` reject `NaN` and infinities
  (`isFinite()`), so downstream arithmetic cannot silently produce `NaN` totals.
- `AssetCode` normalises to **uppercase** and `IconAsset` to **lowercase**: comparisons are
  case-insensitive by construction, so do not pre-normalise at call sites.
- `ColorInt` is a plain `Int` wrapper with public constructor and no validation — it is the one
  exception, because an ARGB value has no meaningful range check.

## Side Effects

- None.

## Verification

- Run: `./gradlew :shared:data:model:testDebugUnitTest`

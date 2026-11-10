# com.ivy.data.model.util — rounding helpers

## Overview

Monetary rounding for display and export. Kept next to the model because rounding a
`PositiveValue` must preserve its asset.

## Interface

- `fun PositiveValue.round(decimalPlaces: Int): PositiveValue`
- `fun Double.roundTo(decimalPlaces: Int): Double`

## Invariants

- Rounding uses `BigDecimal.setScale(decimalPlaces, RoundingMode.HALF_EVEN)` — banker's rounding,
  not `Math.round`. Changing the mode changes totals at the cent boundary.
- `PositiveValue.round` uses `PositiveDouble.unsafe` deliberately: rounding a positive value to
  `>= 0` decimal places cannot make it non-positive, so re-validation is pure overhead.
- `decimalPlaces` must be non-negative; `BigDecimal.setScale` throws otherwise.

## Side Effects

- None.

## Verification

- Run: `./gradlew :shared:data:model:testDebugUnitTest`

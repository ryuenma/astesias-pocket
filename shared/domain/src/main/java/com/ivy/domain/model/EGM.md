# com.ivy.domain.model — domain read models

## Overview

Small result types that belong to the domain layer rather than the data model: a per-currency
statistics summary and the time-range query bound.

## Interface

- `data class StatSummary(val trnCount: NonNegativeInt, val values: Map<AssetCode, PositiveDouble>)`
  with `companion object { val Zero }`.
- `sealed interface TimeRange` — `data object AllTime`, `data class Before(val time: Instant)`,
  `data class After(val time: Instant)`.

## Invariants

- `StatSummary.values` is keyed by `AssetCode`, so stats are **per currency and never summed across
  them**. Collapsing them into a single number requires an explicit exchange rate.
- `StatSummary.Zero` is the identity (`trnCount = 0`, empty map); comparing against it is cheaper and
  safer than constructing a new empty summary.
- `TimeRange.Before`/`After` bounds are **inclusive** (documented on the parameters), and the interface is
  deliberately not `Between`: a range with two bounds is expressed by composing the two filters.
- This `TimeRange` is a different type from `com.ivy.base.time.TimeRange` (which holds the safe
  `Instant` bounds). They are not interchangeable and both are imported in the same files.

## Side Effects

- None (pure data).

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

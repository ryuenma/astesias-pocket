# com.ivy.domain.model (test) — model assertions

## Overview

Kotest assertions for the domain read models.

## Interface

- `infix fun StatSummary.shouldBeApprox(other: StatSummary)` — asserts equal `trnCount`, equal
  `values` keys, then approximate equality per value.

## Invariants

- The assertion compares `trnCount` **exactly** but values **approximately** (`shouldBeApprox` from
  `model-testing`). A test that needs exact amounts must compare `values[...]!!.value` directly.
- Key sets are compared before values, so a missing currency fails as a key mismatch rather than a
  `null` dereference.
- Using the infix form (`actual shouldBeApprox expected`) keeps failure messages readable; a wrapper that
  drops the argument name loses that.

## Side Effects

- None (pure assertion).

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

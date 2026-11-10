# com.ivy.domain.usecase (test) — use-case tests

## Overview

Package root for the domain module's use-case tests, grouped per subject.

## Interface

- Subpackages (outside this partition; no contract files here): `balance/`, `billreminder/`, `csv/`,
  `stat/`, `transfer/`, `xlsx/`.

## Subdirectories & Boundaries

- [billreminder/](./billreminder/EGM.md) — Tests for the bill-reminder calendar arithmetic and inbox, with FakePendingBillStore as the in-memory pending_bills double.

## Invariants

- `billreminder/` is the only subpackage owned by this documentation pass; the rest are equally
  test-only and follow the same JVM-only rule.
- Every test package builds on the fixtures in `com.ivy.domain` (`ArbTransactionsUtil`, `ValueUtil`) and
  the arbiters from `shared/data/model-testing`; duplicating a generator locally is how two tests start
  disagreeing about what a valid transaction is.

## Side Effects

- None.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

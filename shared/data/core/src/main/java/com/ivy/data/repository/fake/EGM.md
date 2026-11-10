# com.ivy.data.repository.fake — repository test fixtures

## Overview

One helper for building a `RepositoryMemo` in tests without a Hilt graph.

## Interface

- `@VisibleForTesting fun fakeRepositoryMemoFactory(): RepositoryMemoFactory` — constructs
  `RepositoryMemoFactory(dataObserver = DataObserver(), dispatchers = TestDispatchersProvider)`.

## Invariants

- The factory uses `TestDispatchersProvider` (`Dispatchers.Unconfined`), so memoised calls run inline
  and tests need no `runTest` scheduler juggling.
- Each call creates a **fresh** `DataObserver`, so two fakes do not share an event stream; a test that
  wants to observe writes must capture the observer itself rather than calling this helper twice.
- `@VisibleForTesting` is advisory only — nothing structurally prevents production code from calling it.

## Side Effects

- Allocates a `DataObserver` and an unconfined `DispatchersProvider` per call.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

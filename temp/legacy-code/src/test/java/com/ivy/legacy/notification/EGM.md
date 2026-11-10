# Bill Reminder Tests

## Overview
The JVM tests for the bill-reminder decision plus the in-memory store they run against. The worker itself cannot be built on the JVM (`CoroutineWorker` needs a real Android `Context`), so what is tested is the decision it delegates to.

## Interface
- `BillReminderNotificationTest` - the decision: notify on first fabrication, stay silent on a replay, and the pending row carries no "seen" flag.
- `FakePendingBillStore` - `@VisibleForTesting` in-memory implementation of `PendingBillDao` + `WritePendingBillDao`, behaving like SQLite where it matters (`insertAttempts`, `refusedInserts`).
- The tested seam is `com.ivy.domain.usecase.billreminder.BillReminderInbox` with `Result.Created` / `Result.AlreadyHandled`.

## Invariants
- These are test sources: they are excluded from the production contract boundary and must not be shipped or referenced from `main`.
- `FakePendingBillStore` is deliberately duplicated rather than shared - the canonical copy lives in `shared/domain`'s test source set, which this module cannot see. `shared/` changes must be mirrored here by hand.
- The rule under test is "notify on first fabrication, never on a replay" - not "notify while unopened" and not "notify until acknowledged". A change that makes a retry re-notify breaks the contract even though the first notification still works.
- The zone under test is `Asia/Jakarta`, matching the fork's user base; a test that hardcodes UTC would pass while the real scheduling is off by seven hours.

## Side Effects
- Test-only: no production state is touched. The fake keeps its rows in memory.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

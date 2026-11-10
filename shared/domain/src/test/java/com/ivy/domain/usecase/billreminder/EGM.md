# com.ivy.domain.usecase.billreminder (test) — reminder tests

## Overview

Unit tests for `BillReminderDates` and `BillReminderInbox`, plus the in-memory store that stands in for
the pending-bill table.

## Interface

- `BillReminderDatesTest` (9 KB) — calendar arithmetic: month-end clamping, year-end rollover, day-of-month
  validation, `dueOnInstant`.
- `BillReminderInboxTest` (12.5 KB) — occurrence fabrication, deduplication, adjust/dismiss/markPaid.
- `FakePendingBillStore` (4.3 KB) — in-memory `pending_bills` double used by the inbox tests.

## Invariants

- `BillReminderDatesTest` must exercise the 28/29/30/31-day months and the December→January boundary:
  that boundary is the reason `BillReminderDates` exists as a pure object in the first place.
- `FakePendingBillStore` must reproduce the `occurrenceKey` uniqueness rule the real table enforces,
  otherwise a deduplication test passes against a store that cannot actually reject a duplicate.
- These tests must not touch `Context`, WorkManager or Room.

## Side Effects

- None: the store is in memory.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest --tests 'com.ivy.domain.usecase.billreminder.*'`

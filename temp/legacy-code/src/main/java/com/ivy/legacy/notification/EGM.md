# Bill & Transaction Reminders

## Overview
Reminder scheduling and the two WorkManager workers behind it. `BillReminderScheduler` enqueues one-shot work at the exact next occurrence of each bill's chosen day; `TransactionReminderLogic`/`TransactionReminderWorker` handle the daily reminder. Package is `com.ivy.wallet.domain.deprecated.logic.notification` even though the directory is `.../legacy/notification`.

## Interface
- `BillReminderScheduler(...)` - one-shot scheduling per bill.
- `BillReminderWorker(...)` - `@HiltWorker` `@AssistedInject` worker: fabricates a pending bill, tells the owner, re-arms next month.
- `TransactionReminderLogic(...)` - the daily reminder decision (`@Deprecated` toward FP style).
- `TransactionReminderWorker(...)` - `@HiltWorker` `@AssistedInject` worker that runs it.

## Invariants
- One-shot scheduling is deliberate: `PeriodicWorkRequestBuilder(24, HOURS)` (what the daily reminder uses) drifts and cannot target "25th at 09:00", so bill reminders are re-armed each run instead.
- The bill worker deliberately does **not** create a transaction and does **not** call `PlannedPayment`-style logic: it fabricates a pending bill, notifies, re-arms. Widening it would make opening the app irrelevant to whether money is recorded.
- The notification must fire independently of whether the pending row was ever opened: "a pending bill exists" is not evidence "the owner was told", and "the owner acted" is not a precondition for telling them. The rule is: notify on first fabrication, never on a replay.
- Both workers are `@AssistedInject` + `@HiltWorker`, so they are constructed by WorkManager's factory, not by `@Inject`.

## Side Effects
- Enqueues WorkManager work (one-shot per bill, re-armed monthly).
- Posts notifications through `NotificationService` / `IvyNotificationId`.
- Writes pending-bill rows and reads the planned-payment rules.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

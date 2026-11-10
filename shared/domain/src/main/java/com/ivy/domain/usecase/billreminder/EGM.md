# com.ivy.domain.usecase.billreminder — monthly bill reminders

## Overview

Pure calendar arithmetic for monthly bill reminders plus the inbox that materialises them as pending
bills. `BillReminderDates` exists because WorkManager's `PeriodicWorkRequest` cannot express "the 25th
of each month".

## Interface

- `object BillReminderDates`
  - `val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)`
  - `fun isValidDayOfMonth(dayOfMonth: Int?): Boolean`
  - `fun clampToMonth(month: YearMonth, dayOfMonth: Int): LocalDate`
  - `fun nextOccurrence(...)`
  - `fun dueOnInstant(localDate: LocalDate, zone: ZoneId): Instant`
- `class BillReminderInbox @Inject constructor(...)`
  - `fun occurrenceKey(ruleId: UUID, dueOn: LocalDate): String`
  - `suspend fun fabricate(...)`
  - `suspend fun pendingBills(): List<PendingBillEntity>`
  - `suspend fun adjustAmount(occurrenceKey: String, amount: Double): Boolean`
  - `suspend fun dismiss(occurrenceKey: String): Boolean`
  - `suspend fun markPaid(occurrenceKey: String): PendingBillEntity?`
  - `suspend fun billsForRule(ruleId: UUID): List<PendingBillEntity>`
  - `suspend fun deleteAllForRule(ruleId: UUID): Unit`

## Invariants

- **Reminders are one-shot, not periodic.** A 24-hour periodic request cannot sit on a calendar day
  (months are 28–31 days, so it drifts within two months), WorkManager defers periodic work under Doze
  so a reminder can land after midnight, and the existing worker never re-enqueues. So
  `nextOccurrence` computes an absolute `Instant`, the scheduler enqueues a one-shot with an initial
  delay, and the worker re-arms the following month.
- `REMINDER_TIME` is **09:00 local**, deliberately different from the daily transaction reminder's 20:00.
- Day-of-month 31 is accepted because it is real in seven months; `clampToMonth` gives it a meaning in
  the four where it is not (Feb/Apr/Jun/Sep/Nov), so a rule never fires on a non-existent date.
- `occurrenceKey(ruleId, dueOn)` is the **identity of one occurrence** and must be
  deterministic and stable — the inbox uses it to deduplicate, to address `adjustAmount`/`dismiss`/
  `markPaid`, and as the idempotency handle for a replayed worker run.
- `BillReminderDates` is an `object` with no Android, database or WorkManager dependency so month-end and
  year-end arithmetic can be tested on the JVM. Keep it that way.
- `BillReminderInbox` returns `Boolean`/nullable results for mutations, so a caller can distinguish "no
  such occurrence" from a successful change.

## Side Effects

- `BillReminderInbox` writes `pending_bills` rows (fabricate/adjust/dismiss/markPaid/delete).
- `dueOnInstant` applies a caller-supplied `ZoneId` — no implicit system-zone read.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`
- Tests: `shared/domain/src/test/java/com/ivy/domain/usecase/billreminder/`

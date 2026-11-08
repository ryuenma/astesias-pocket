# Transfer Answers (com.ivy.transaction.answers)

## Overview
Destination that shows what the counterpart decided about transfers this device
sent. It is the sender-side counterpart to the pending-transfers inbox: without
it, a sender cannot tell "still waiting" from "declined and nobody told me".

## Interface
- `TransferAnswersViewModel` — `@HiltViewModel`, plain `ViewModel`; injects `TransferConfirmationRepository`, `TransferEventSync`, `TransferOutboxRepository`. Exposes `answers: ImmutableList<TransferConfirmationWithEvent>`.
- `load()` — drains the outbox, pulls confirmations, reads `repository.answered()`, then acknowledges.
- `BoxWithConstraintsScope.TransferAnswersScreen(screen: TransferAnswersScreen)` — screen entry point, test tag `transfer_answers_screen`; the `screen` parameter is unused (the destination has no arguments).
- `BoxScope.TransferAnswersModal(visible, answers, onDismiss, ...)`, plus private `NoAnswersYet`, `TransferAnswerRow`, `parseDateOrNull`.

## Invariants
- Load order is fixed: **publish what this device owes first → pull → read → acknowledge**. Publishing first means nothing is claimed about an answer that has not left this phone.
- `TransferConfirmationRepository.pullAnswers` persists, so the list is read from the durable table, not from the pull response — that is what makes it survive screen close.
- Acknowledge happens **after** the read and only once the refresh finished; acknowledging first would clear the badge for rows that never rendered.
- `load()` never throws: sync failures are logged and the last known rows stay on screen.
- **The screen has two states only: recorded rows, or `NoAnswersYet`.** It does not distinguish "still waiting" from "declined and nobody told me". `runCatching { pullConfirmations() }` discards the returned outcome (`NotConfigured` / `Unreachable` / `Pulled(0,0,0,0)`) because those are values, not exceptions — so with an expired token (server answers `200`/`totalItems: 0`) an accepted answer renders as "No answers yet" with no error.
- **`acknowledgeAll()` is an unfiltered `UPDATE … WHERE acknowledged = 0`, run unconditionally** — including after the read threw, and including rows the list's `INNER JOIN … kind = 'TRANSFER_EVENT'` excludes while `countUnacknowledged()` still counts them. Badge and list can disagree, and opening the screen erases the evidence.
- `onDismiss` is `nav.back()`, so Done, the close control and the system back gesture all mean "finished looking".
- The named verification command does not exercise this path (the module's `src/test` holds only `DefaultTimeOfDayTest.kt`).

## Side Effects
- Network: `TransferEventSync.pullConfirmations()`, `TransferOutboxRepository.drain()`.
- Database: reads and acknowledges `TransferConfirmationRepository` rows.
- Navigation: pops the destination on dismiss.

## Verification
- Run: `./gradlew :feature:edit-transaction:testDebugUnitTest` (unit tests in `src/test`).

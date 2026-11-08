# Pending Transfers (com.ivy.transaction.pending)

## Overview
Review surface for incoming cross-profile transfers. It exists because the inbox
used to render only from inside the transaction form behind a flag nothing set:
money arrived in `pending_transfers` with no affordance to accept or decline it.
The inbox is now its own destination reachable from Settings.

## Interface
- `PendingTransfersViewModel` — `@HiltViewModel`, plain `ViewModel`; injects `PendingTransferRepository`, `TransferEventSync`, `TransferOutboxRepository`. Exposes `items: ImmutableList<PendingTransferEntity>`.
- `load()` — pulls events, drains the outbox, reads `repository.pending()`.
- `accept(entry)` / `decline(entry)` — apply a `TransferInbox.Action` decision.
- `BoxWithConstraintsScope.PendingTransfersScreen(screen: PendingTransfersScreen)` — screen entry point, test tag `pending_transfers_screen`; the `screen` parameter is unused.
- `BoxScope.PendingTransfersModal(visible, items, onAccept, onDecline, onDismiss, ...)`, private `NothingPending`, `PendingTransferRow`, `formatPendingAmount`.

## Invariants
- Pull happens **before** the read, so the list is never a stale inbox that silently corrects itself.
- Every sync step is best-effort (`runCatching` + `Timber.e`); an unreachable server must still open the screen with the last known rows.
- Accept is **two separate writes, in this order**: `markDecided()` commits `ACCEPTED`, then `transactionDao.save(receiverLeg(...))` writes the ledger row. There is **no enclosing transaction** — a crash or failure between them leaves `ACCEPTED` with no ledger row.
- **The failure is swallowed and unrecoverable.** The call site wraps the whole thing in `runCatching{…}.onFailure{ Timber.e(...) }.getOrNull()`; release builds plant no `Timber` tree, so the failure is silent. A failed ledger write leaves `ACCEPTED` with no row, shows nothing, and every retry is a no-op because `findPending` only returns `PENDING` rows.
- The doc's earlier justification — "both re-read the inbox afterwards so the list can never claim a decision the database refused" — is false: the re-read is the *inbox*, which now says `ACCEPTED` regardless of whether the ledger write landed.
- Decline writes only a tombstone and leaves the ledger untouched.
- The sender account id is derived deterministically: `UUID.nameUUIDFromBytes("pending-transfer-sender:<correlationId>")`, so a retry names the same counterparty instead of inventing a second identity. The written row's `accountId` is this synthetic value, readable only while `counterpartProfileId != null`; `receiverLeg` sets it (`CrossProfileTransfer.kt:95`), so this is a fragile coupling rather than a proven defect.
- The answer is enqueued for publication **only** when the applied decision is not `NoChange` — a double tap must not post a second, contradictory answer.
- After a decision the list is re-read from the repository rather than mutated optimistically.
- `onDismiss` is `nav.back()`.

## Side Effects
- Network: `TransferEventSync.pullEvents()`, `TransferOutboxRepository.drain()` and `enqueueConfirmation(...)`.
- Database writes: `PendingTransferRepository.decide(...)` (ledger row on accept, tombstone on decline);
  `pullEvents()` inserts `pending_transfers`; `drain()` and `enqueueConfirmation` write `transfer_outbox`.
- Navigation: pops the destination on dismiss.

## Verification
- Run: `./gradlew :feature:edit-transaction:testDebugUnitTest` (unit tests in `src/test`).

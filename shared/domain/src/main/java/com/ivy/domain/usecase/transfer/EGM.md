# com.ivy.domain.usecase.transfer — cross-profile transfers

## Overview

The two-device money-transfer pipeline: composing the two ledger legs, publishing them, pulling the
other device's events, deciding whether to accept, and recording the answer. This is the most
stateful area of the domain layer.

## Interface

- `object CrossProfileTransfer` — `fun senderLeg(...)`, `fun receiverLeg(...)`.
- `@Singleton class CrossProfileTransferPublisher @Inject constructor(...)` — `suspend fun publishRecorded(...)`.
- `@Singleton class PendingTransferRepository @Inject constructor(...)` —
  `suspend fun onEventArrived(item: PendingTransferEntity): TransferInbox.Outcome`,
  `suspend fun pending(): List<PendingTransferEntity>`, `suspend fun decide(...)`.
- `object TransferInbox` — `fun onEventArrived(existing: PendingTransferState?): Outcome`,
  `fun onUserDecision(existing: PendingTransferState?, action: Action): Decision`.
- `data class/type PendingTransferState` (state model for an arriving transfer).
- `@Singleton class TransferConfirmationRepository @Inject constructor(...)` —
  `suspend fun pullAnswers(): AnswerPullResult`, `suspend fun answered(): List<TransferConfirmationWithEvent>`,
  `suspend fun unacknowledgedCount(): Int`, `suspend fun acknowledgeAll(): Int`.
- `@Singleton class TransferEventSync @Inject constructor(...)` — `suspend fun pullEvents(): PullResult`,
  `suspend fun pullConfirmations(): TransferConfirmationRepository.AnswerPullResult`.
- `@Singleton class TransferSyncRunner @Inject constructor(...)` — `suspend fun runOnce(): Boolean`.

## Invariants

- **The two legs are separate transactions.** `senderLeg` and `receiverLeg` produce rows in two different
  databases, linked only by `linkedTransactionId` (the correlation id, which is also the idempotency
  key). Neither side can see the other's account ids, which is why the receiver addresses its
  destination by **account name**.
- `TransferInbox` is a **pure decision function** over `PendingTransferState?`: `onEventArrived` decides
  what a replayed/duplicate/conflicting arrival means, and `onUserDecision` maps a user action to a
  `Decision`. Keeping it pure is what makes the duplicate-arrival rules testable without a database.
- Arrival with an existing state is a **duplicate or conflict**, not a fresh insert — the state machine
  in `TransferInbox.onEventArrived` is the authority on which, and a caller must not shortcut it.
- `TransferSyncRunner.runOnce()` returns `Boolean` for "did anything happen"; a `false` is not an error.
- Sending goes through `TransferOutboxRepository` in `data/core` (enqueue then drain), so a retry cannot
  double-post; see `com.ivy.data.sync`.
- `TransferConfirmationRepository.acknowledgeAll()` returns the number acknowledged, so the UI can report
  it; `unacknowledgedCount()` is the badge source.
- Orchestration lives here rather than in `data/core` because `data/core` cannot depend on `domain`.

## Side Effects

- Writes/reads `pending_transfers`, `transfer_outbox` and `transfer_confirmations` rows.
- `CrossProfileTransferPublisher`, `TransferEventSync` and `TransferSyncRunner` perform network calls.
- `PendingTransferRepository.decide` materialises a transaction on the receiving device.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

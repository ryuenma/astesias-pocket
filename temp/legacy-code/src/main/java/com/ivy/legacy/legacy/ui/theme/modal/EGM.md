# Legacy Modals

## Overview
The inherited modal/bottom-sheet family: the `IvyModal` chrome plus delete/name/progress modals, the loan and loan-record editors, the recurring-rule editor and the sync-server login modal. Everything here is deprecated in favour of `:ivy-design` + Material3, and `IvyModal` is the piece `.../modal/edit` and the legacy screens build on.

## Interface
- `IvyModal(...)` (`BoxScope` extension) plus `DURATION_MODAL_ANIM`, `AddModalBackHandling(...)`, `ModalActionsRow(...)` - the modal chrome and back-press handling.
- `ModalDynamicPrimaryAction`, `ModalSet`, `ModalCheck`, `ModalSave`, `ModalAdd`, `ModalNegativeButton`, `ModalPositiveButton`, `ModalDelete`, `ModalTitle`, `ModalSkip` (`IvyModalComponents.kt`) - the action-button vocabulary.
- `ModalAmountSection(...)` (`IvyModalDomainComponents.kt`), `ModalNameInput(...)` (`LegacyModals.kt`).
- `DeleteModal(...)`, `DeleteConfirmationModal(...)`, `NameModal(...)`, `ProgressModal(...)`, `BufferModal(BufferModalData, ...)`, `CurrencyModal(...)`, `AddKeywordModal(...)`, `ChooseIconModal(...)`, `ChoosePeriodModal(ChoosePeriodModalData, ...)` + `MonthYear`, `ChooseStartDateOfMonthModal(...)`.
- `LoanModal(LoanModalData, ...)`, `LoanRecordModal(LoanRecordModalData, ...)`, `RecurringRuleModal(RecurringRuleModalData, ...)`, `SyncServerModal(...)` with `SyncSignInResult`.

- [edit](./edit/EGM.md) - Edit-transaction modals: account, amount, calculator, category, choose-category and description.

## Invariants
- Every modal is a `BoxWithConstraintsScope` extension except `IvyModal`, which is a `BoxScope` extension - callers must be inside `BoxWithConstraints`.
- `SyncSignInResult` is deliberately a separate type from the data-layer result so this legacy UI module stays free of networking types; the password is cleared as soon as it is handed to `onSignIn`, so it never sits in a recomposed snapshot.
- Modals here do not persist anything: `save`-style lambdas assemble arguments and call the caller's callback.
- `IvyModal.kt` (353L), `ChooseIconModal.kt` (750L), `ChoosePeriodModal.kt` (596L), `LoanModal.kt` (670L), `LoanRecordModal.kt` (681L), `RecurringRuleModal.kt` (439L) and `SyncServerModal.kt` (361L) exceed the 300-line read cap; declarations listed come from the first 300 lines.

## Side Effects
- `AddModalBackHandling` registers a navigation back handler and `removeLastBackHandlerSafe` removes it - a leaked handler swallows the back press on other screens.
- Animation durations (`DURATION_MODAL_ANIM = 200`, background blur `400`) are shared timing constants; changing them changes every legacy modal.
- `SyncServerModal` performs the sign-in through the callback it is given (network happens in the caller's layer, not here).

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

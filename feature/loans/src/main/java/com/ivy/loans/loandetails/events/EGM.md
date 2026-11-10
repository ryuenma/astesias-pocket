# Loan Details Events (com.ivy.loans.loandetails.events)

## Overview
Event vocabulary for the loan-details screen, split by modal so each family is
dispatched to its own handler in `LoanDetailsViewModel`. All four interfaces extend
`LoanDetailsScreenEvent`.

## Interface
- `LoanDetailsScreenEvent` — root sealed interface: `OnEditLoanClick`, `OnAmountClick`, `OnAddRecord`, `OnCreateAccount(CreateAccountData)`.
- `LoanModalEvent : LoanDetailsScreenEvent` — `OnDismissLoanModal`, `OnEditLoanModal(loan, createLoanTransaction)`, `PerformCalculation`, `OnChangeDate`, `OnChangeTime`.
- `LoanRecordModalEvent : LoanDetailsScreenEvent` — `OnClickLoanRecord(DisplayLoanRecord)`, `OnCreateLoanRecord(CreateLoanRecordData)`, `OnDeleteLoanRecord(LoanRecord)`, `OnEditLoanRecord(EditLoanRecordData)`, `OnDismissLoanRecord`, `OnChangeDate`, `OnChangeTime`.
- `DeleteLoanModalEvent : LoanDetailsScreenEvent` — `OnDeleteLoan`, `OnDismissDeleteLoan(isDeleteModalVisible)`.

## Invariants
- Every event is a subtype of `LoanDetailsScreenEvent`; the ViewModel's `when` must handle all four families, so an unhandled new subtype fails the build rather than being silently ignored.
- `OnChangeDate`/`OnChangeTime` exist independently in `LoanModalEvent` and `LoanRecordModalEvent` — they are not interchangeable, because each modal edits a different entity.
- `OnEditLoanModal(createLoanTransaction)` carries the flag that decides whether a ledger transaction accompanies the loan change.
- `OnDismissDeleteLoan` carries the resulting visibility, so dismissal and visibility are one event rather than two.

## Side Effects
- None. Pure event declarations (the handlers in `LoanDetailsViewModel` perform the writes).

## Verification
- Run: `./gradlew :feature:loans:testDebugUnitTest` (unit tests in `src/test`).

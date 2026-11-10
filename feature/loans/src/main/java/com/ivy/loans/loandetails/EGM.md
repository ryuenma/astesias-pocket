# Loan Details (com.ivy.loans.loandetails)

## Overview
Detail screen for one loan: header with the total and amount paid, the list of loan
records, and the modals to edit the loan, add/edit/delete a record and delete the
loan. Events are split across four files in `events/`.

## Interface
- `BoxWithConstraintsScope.LoanDetailsScreen(screen: LoanDetailsScreen)` — entry point (carries the loan id).
- `LoanDetailsScreenState` — `baseCurrency`, `loan: Loan?`, `displayLoanRecords`, `loanTotalAmount`, `amountPaid`, `loanAmountPaid`, `accounts`, `selectedLoanAccount`, `createLoanTransaction`, `loanModalData`, `loanRecordModalData`, delete-modal visibility fields.
- `LoanDetailsViewModel` — `@HiltViewModel`, `ComposeViewModel<LoanDetailsScreenState, LoanDetailsScreenEvent>`; injects `LoanRecordDao`, `LoanCreator`, `LoanRecordCreator`, `SettingsDao`, `TransactionRepository`, `TransactionMapper`, `AccountCreator`, `LoanTransactionsLogic`, …
- `start()` / `load(loanId: UUID)` — load a loan by id; `handleLoanRecordModalEvents`, `handleLoanModalEvents`, `handleDeleteLoanModalEvents`, `handleLoanDetailsScreenEvents` dispatch the event families.
- Private UI: `UI`, `Header`.
- Subdirectory: `./events/EGM.md`.

## Invariants
- `loan` is nullable: the screen must render an empty/absent state while loading or when the id no longer exists.
- Each event family is handled by exactly one `handle…Events` function, so a new event must be added to the matching file in `events/` and its handler together.
- `createLoanTransaction` decides whether saving a record also writes a ledger transaction — a record edit must not silently create one.
- `loanAmountPaid` and `amountPaid` are both exposed because the header shows the paid amount while the record list totals it independently.

## Side Effects
- Reads `LoanRecordDao.findAllByLoanId(loanId)` and resolves each record's transaction through `transactionRepository.findLoanRecordTransaction(...)`.
- Writes loans/records through `LoanCreator`, `LoanRecordCreator`, `WriteLoanDao` and creates ledger transactions via `TransactionRepository`/`TransactionMapper`.
- Creates accounts on demand through `AccountCreator` when a record needs one.
- Reads base currency from `SettingsDao`.

## Verification
- Run: `./gradlew :feature:loans:testDebugUnitTest` (unit tests in `src/test`).

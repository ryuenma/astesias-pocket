# Loan Transactions

## Overview
Turns a loan (and its records) into transactions, which is how a loan shows up in balances and history. `LoanTransactionsCore` is the engine; the two mappers convert between the legacy loan models and the transaction shape the core emits.

## Interface
- `LoanTransactionsCore(...)` - creates/updates the transactions for a loan (344 lines; first 300 read).
- `LTLoanMapper(...)` - maps a loan into transaction arguments.
- `LTLoanRecordMapper(...)` - maps a loan record into transaction arguments.
- `LoanTransactionsLogic(...)` - `data class` wrapper that composes the above for callers.

## Invariants
- The directory name is misspelled (`loantrasactions`) and is load-bearing: renaming it changes both the path and the `com.ivy.*` package of its files.
- `LoanTransactionsCore` writes transactions; running it twice for the same loan record must be idempotent - duplicate rows are the failure mode to check.
- `LoanTransactionsCore.kt` exceeds the 300-line read cap.

## Side Effects
- Writes transactions to the database through the injected repositories.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

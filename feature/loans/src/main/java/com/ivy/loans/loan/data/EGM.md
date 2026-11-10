# Loan Display Models (com.ivy.loans.loan.data)

## Overview
Presentation models for the loans screens: a loan plus its computed progress, and a
loan record paired with the account and currency context needed to render it.

## Interface
- `DisplayLoan(loan: Loan, loanTotalAmount: Double, amountPaid: Double, currencyCode: String? = getDefaultFIATCurrency().currencyCode, formattedDisplayText: String = "", percentPaid: Double = 0.0)` — implements `Reorderable`.
- `DisplayLoan.getItemOrderNum(): Double` — returns `loan.orderNum`; `withNewOrderNum(newOrderNum)` — copies with the new `orderNum`.
- `DisplayLoanRecord(loanRecord: LoanRecord, account: Account? = null, loanRecordCurrencyCode: String = "", loanCurrencyCode: String = "", loanRecordTransaction: Boolean = false)`.

## Invariants
- `amountPaid`, `formattedDisplayText` and `percentPaid` are derived values; the persisted truth is `Loan` fields only.
- `withNewOrderNum` returns the `Reorderable` supertype — callers cast back to `DisplayLoan`.
- `currencyCode` falls back to the device's default fiat currency when the loan carries none.
- `DisplayLoanRecord.account` is nullable: a record can reference an account that no longer resolves, and the UI must handle `null`.

## Side Effects
- None. Plain data classes.

## Verification
- Run: `./gradlew :feature:loans:testDebugUnitTest` (unit tests in `src/test`).

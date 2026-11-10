# Loan Actions

## Overview
Loan list and single-loan lookups.

## Interface
- `LoansAct` - all loans.
- `LoanByIdAct` - one loan by id (nullable when absent).

## Invariants
- `LoanByIdAct` returns nothing for a missing id rather than throwing, so callers must handle absence.
- Loans carry their own transactions via `domain/deprecated/logic/loantrasactions`; these actions only read the loan entities.

## Side Effects
- Repository reads only.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

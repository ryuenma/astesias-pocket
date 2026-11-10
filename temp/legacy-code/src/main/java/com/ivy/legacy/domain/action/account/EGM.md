# Account Actions

## Overview
Account reads plus the per-account aggregates the account screen shows: balance, income and expense, and that account's transactions.

## Interface
- `AccountsAct`, `AccountByIdAct` - list and single lookup.
- `AccTrnsAct` - the transactions of one account.
- `CalcAccBalanceAct` - the account balance.
- `CalcAccIncomeExpenseAct` - income and expense for one account.

## Invariants
- `CalcAccBalanceAct` must agree with `CalcWalletBalanceAct` for an account set of one - the wallet total is the sum over accounts, so any rule applied in one place and not the other shows up as a wallet/account mismatch.
- Account currency differs from the base currency, so the aggregate actions convert through exchange rates.
- All read-only.

## Side Effects
- Repository and exchange-rate reads.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

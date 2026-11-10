# Wallet Aggregate Actions

## Overview
Wallet-level totals across every account: the balance and the income/expense split.

## Interface
- `CalcWalletBalanceAct` - the wallet balance across accounts.
- `CalcIncomeExpenseAct` - income and expense totals for the wallet.

## Invariants
- Both must skip accounts flagged excluded - `filterExcluded` in `domain/pure/account` is that rule, and a manual `filter` here would drift from it.
- Totals are returned in the base currency, so the actions depend on exchange rates.
- Both are read-only; the wallet balance is derived, never stored.

## Side Effects
- Repository and exchange-rate reads.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

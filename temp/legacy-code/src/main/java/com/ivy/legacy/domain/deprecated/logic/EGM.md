# Deprecated Domain Logic

## Overview
The creator and logic classes that actually perform writes (and the preload/smart-suggestion helpers around them). This is the layer the legacy screens call before any FP-style action exists for an operation, and several of these are the single funnel for their entity.

## Interface
- Writers: `AccountCreator`, `CategoryCreator`, `BudgetCreator`, `LoanCreator`, `LoanRecordCreator`.
- `CategoryCreator.CategorySaveResult` - sealed interface reporting what happened to a create/edit request.
- `PlannedPaymentsLogic` + `PlannedPaymentsGenerator` - recurring/planned payment handling.
- `PreloadDataLogic` - initial data seeding, `@Deprecated("Legacy, get rid of it.")`.
- `SmartTitleSuggestionsLogic` with `SUGGESTIONS_LIMIT = 10` - title suggestions (`@Deprecated` toward FP style).
- `WalletAccountLogic`, `WalletCategoryLogic` - older wallet-level read/write helpers.
- CSV, currency, loan-transaction and model sub-packages sit below.

- [csv](./csv/EGM.md) - CSV import pipeline: `CSVImporter`, `CSVMapper`, `CSVNormalizer`.
- [currency](./currency/EGM.md) - `ExchangeRatesLogic` and the deprecated `sumInBaseCurrency` helpers.
- [loantrasactions](./loantrasactions/EGM.md) - Loan transaction generation: mappers and `LoanTransactionsCore`, which turns a loan into transactions.
- [model](./model/EGM.md) - Plain `Create*Data` / `Edit*Data` argument holders for the creator classes.

## Invariants
- `AccountCreator` is the one funnel every account create/edit in the app goes through - six call sites across five feature modules land on its two functions, so publishing lives there rather than in the callers.
- `CategoryCreator` must **report** uniqueness rejection, not just enforce it: a caller that gets nothing back cannot distinguish "saved" from "rejected as duplicate". That is why `CategorySaveResult` exists.
- `PlannedPaymentsLogic.kt` (328L) and `PreloadDataLogic.kt` (335L) exceed the 300-line read cap, as does `WalletCategoryLogic.kt` (420L).
- Most classes here are deprecated toward the FP style; the deprecation is not a licence to bypass them - they are still the live write path for legacy screens.

## Side Effects
- Database writes through injected DAOs/repositories (creators, preloader, planned payments).
- `PreloadDataLogic` seeds default categories/accounts on first run, so it is not safe to call on every launch.
- `SmartTitleSuggestionsLogic` counts title usage, i.e. reads the transaction table repeatedly.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

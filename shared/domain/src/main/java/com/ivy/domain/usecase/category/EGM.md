# com.ivy.domain.usecase.category — category statistics

## Overview

Aggregates transactions per category, in raw and exchanged forms.

## Interface

- `class CategoryStatsUseCase @Inject constructor(...)` — four `suspend fun calculate(...)` overloads
  returning `CategoryStats` / `ExchangedCategoryStats`.
- `data class CategoryStats(...)`, `data class ExchangedCategoryStats(...)`.

## Invariants

- Category statistics are computed from the transaction list, **not** from the `CategoryTree` parent
  links: a parent's total does not include its subcategories unless the caller passes the rolled-up id
  set from `CategoryTree.rollupIds`.
- "Unspecified category" (a transaction with `category == null`) is a distinct bucket and must not be
  folded into any real category's total.
- The exchanged overloads require a rate for every currency present; a missing rate leaves that currency
  out of the exchanged total rather than converting it at 1:1.
- Suppressions (`UnusedPrivateProperty`, `UnusedParameter`) acknowledge that not every overload uses
  every injected repository.

## Side Effects

- Reads transactions, categories and exchange rates through repositories.
- Exchanged overloads may trigger rate lookups.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

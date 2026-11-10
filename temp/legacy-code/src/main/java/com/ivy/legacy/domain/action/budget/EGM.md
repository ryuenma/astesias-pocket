# Budget Action

## Overview
The single action that reads budgets.

## Interface
- `BudgetsAct(...)` - all budgets.

## Invariants
- Budgets store their category and account ids as serialised strings (`BudgetEntity.parseCategoryIds()`/`parseAccountIds()` in `datamodel/temp`); this action returns the deserialised models, so callers never see the raw strings.
- Read-only.

## Side Effects
- Repository reads only.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

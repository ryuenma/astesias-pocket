# Creator Argument Types

## Overview
The plain `Create*Data` / `Edit*Data` argument holders the creator classes accept. They exist so the creator signatures stay stable while fields are added.

## Interface
- `CreateAccountData`, `CreateBudgetData`, `CreateCategoryData`, `CreateLoanData`, `CreateLoanRecordData`, `EditLoanRecordData`.
- `CreateCategoryData` carries `@Suppress("DataClassDefaultValues", "DataClassTypedIDs")` - it intentionally uses defaults and untyped ids.

## Invariants
- All are pure data: no behaviour, no validation. Validation happens in the creator that receives them.
- Adding a field with a default keeps every existing call site source-compatible; adding one without a default breaks them - that trade-off is deliberate here.

## Side Effects
- None.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

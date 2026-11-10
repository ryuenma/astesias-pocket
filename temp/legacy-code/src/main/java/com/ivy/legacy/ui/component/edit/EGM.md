# Edit Transaction Building Blocks

## Overview
Two leaf building blocks shared by the edit screens: the primary-attribute column (the left-hand column that shows account/category/date chips) and the date/time field.

## Interface
- `PrimaryAttributeColumn(...)` - left column of the edit sheet.
- `TransactionDateTime(...)` - date and time selector for a transaction; annotated `@Suppress("MultipleEmitters")` and deprecated in favour of `:ivy-design`.

- [core](./core/EGM.md) - The edit-transaction bottom sheet and its core slots (title, description, category, due date, toolbar).

## Invariants
- `TransactionDateTime` deliberately emits both a date and a time change; the `MultipleEmitters` suppression marks that as intentional, not an oversight.
- Both are stateless: the current value arrives as a parameter and every edit goes back through a callback.

## Side Effects
- None beyond callbacks; no I/O.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

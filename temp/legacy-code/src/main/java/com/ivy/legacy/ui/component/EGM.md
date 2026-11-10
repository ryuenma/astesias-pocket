# Shared Legacy Components

## Overview
Two components shared above the design system: the income/expense summary cards and the item statistics toolbar. Both are deprecated in favour of `:ivy-design` but still used by legacy screens.

## Interface
- `IncomeExpensesCards(...)` with private `HeaderCard` - the paired income and expense summary cards.
- `ItemStatisticToolbar(...)` - the small toolbar shown above per-item statistics.

- [edit](./edit/EGM.md) - Edit-transaction building blocks: primary attribute column and date/time field.
- [tags](./tags/EGM.md) - Tag UI: add/view tags button, show-tags modal and add-or-edit-tag modal.
- [transaction](./transaction/EGM.md) - Transaction list UI: `TransactionCard`, section/date dividers and the `transactions` LazyList builder.

## Invariants
- `IncomeExpensesCards` renders the pair it is given; it does not compute sums - those come from `../../domain/action/transaction` aggregates.
- Both are deprecated by annotation.

## Side Effects
- None; pure rendering.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

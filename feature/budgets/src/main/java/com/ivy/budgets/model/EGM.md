# Budget View Models (com.ivy.budgets.model)

## Overview
Presentation models for the budgets screen. `DisplayBudget` pairs a persisted
`Budget` with the amount already spent in it, and implements `Reorderable` so the
reorder machinery can rewrite its order number without the UI knowing the storage
field.

## Interface
- `DisplayBudget(budget: Budget, spentAmount: Double)` — `@Immutable`, implements `Reorderable`.
- `getItemOrderNum(): Double` — returns `budget.orderId`.
- `withNewOrderNum(newOrderNum: Double): Reorderable` — returns a copy with `budget.orderId = newOrderNum`.

## Invariants
- `spentAmount` is derived, never persisted; recomputing it must not touch `budget`.
- `withNewOrderNum` returns the `Reorderable` supertype — callers must cast back to `DisplayBudget` (or use the generic reorder helper).
- Order identity is `Budget.orderId`, the same field the DAO orders by.

## Side Effects
- None. Plain data class; no I/O and no Android dependencies.

## Verification
- Run: `./gradlew :feature:budgets:assembleDebug` (this module has no `src/test` source set).

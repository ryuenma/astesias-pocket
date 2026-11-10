# Legacy Edit Modals

## Overview
The modal family used while editing a transaction: pick an account, type or calculate an amount, pick a category (flat modal or full-screen tree picker) and edit the description. Every entry point is a `BoxWithConstraintsScope` extension so a screen inside `BoxWithConstraints` can open the modal without extra plumbing, and every file is annotated `@Deprecated("Old design system. Use `:ivy-design` and Material3")`.

## Interface
- `AccountModal(AccountModalData, ...)` - account picker; `AccountModalData` carries the initial account and the account list.
- `AmountModal(...)`, with public `AmountCurrency(...)` and `AmountInput(...)` used by other callers to render an amount with its currency.
- `CalculatorModal(...)` - keypad and expression evaluator for the amount field.
- `CategoryModal(CategoryModalData, ...)` - flat category picker.
- `ChooseCategoryModal(...)` - hierarchical picker; its `PickerRow` model is a private sealed interface so a sub-category cannot leak into the top level by construction.
- `DescriptionModal(...)` - multi-line description editor.

## Invariants
- Every modal takes an `onDismiss`-style callback and a `save` lambda; the private `save(...)` helpers only assemble arguments and invoke the caller's callback, they never write to a repository.
- `AmountModal`, `AccountModal`, `CategoryModal`, `LoanModal`-style modals rely on `IvyModal`/`IvyModalComponents` from `../EGM.md`'s sibling package for chrome and animation.
- Amount text is normalised and formatted through `com.ivy.legacy.utils` (`formatInputAmount`, `format`), so locale separators are handled there and not here.
- `AccountModal.kt` (500L), `AmountModal.kt` (509L), `CategoryModal.kt` (532L) and `ChooseCategoryModal.kt` (455L) all exceed the 300-line read cap; declarations listed here come from the first 300 lines.

## Side Effects
- State changes only through the callbacks the caller supplies; the modals own transient Compose state (text fields, expansion flags).
- `CalculatorModal` formats the expression with the supplied currency symbol before display.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

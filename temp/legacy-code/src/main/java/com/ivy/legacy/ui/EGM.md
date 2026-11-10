# Legacy UI Helpers

## Overview
Package `com.ivy.legacy.ui`: the one legacy UI helper that depends on neither the old design system root nor the theme tree.

## Interface
- `SearchInput(...)` - the search text field used by legacy list screens.

- [component](./component/EGM.md) - Shared legacy UI components above the design system (`IncomeExpensesCards`, `ItemStatisticToolbar`).

## Invariants
- Kept deliberately minimal: anything that needs `IvyModal`, theme colours or the component set belongs in `./component` or `./component/...`, not here.
- Deprecated in favour of `:ivy-design`.

## Side Effects
- None; pure rendering.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

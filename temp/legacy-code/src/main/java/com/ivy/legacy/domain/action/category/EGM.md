# Category Actions

## Overview
Category-scoped transaction queries: the full category tree under account filters, and the transactions between two dates for one category.

## Interface
- `CategoryIncomeWithAccountFiltersAct(...)` - every category (parents and children) with their income under account filters, including the rollup of children into parents.
- `LegacyCategoryIncomeWithAccountFiltersAct(...)` - the same over the legacy transaction type, `@Deprecated("Uses legacy Transaction")`.
- `CategoryTrnsBetweenAct(...)` with `actInput(...)` - transactions in a category between two dates.

## Invariants
- The rollup needs the complete category list, not just the ids: a category's id says nothing about which children it owns. The child list parameter defaults to empty so an existing caller without a list keeps its previous behaviour.
- Parent totals must include children; filtering children out before the rollup silently under-reports every parent category.
- `actInput(...)` is the only supported way to build the input.

## Side Effects
- Repository reads only.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

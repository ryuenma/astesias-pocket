# Categories Package (com.ivy.categories)

## Overview
Categories screen: lists categories with their monthly balance, income and expenses,
supports reordering, sorting, search and the create/edit category modal. Monthly
figures come from `TrnsWithRangeAndAccFiltersAct`; ordering is persisted on each
category row.

## Interface
- `CategoriesScreenEvent` — sealed interface: `OnReorder(newOrder: List<CategoryData>, sortOrder: SortOrder)`, `OnCreateCategory(CreateCategoryData)`, `OnReorderModalVisible(Boolean)`, `OnSortOrderModalVisible(Boolean)`, `OnCategoryModalVisible(CategoryModalData?)`, `OnSearchQueryUpdate(queryString: String)`.
- `CategoriesScreenState` — `compactCategoriesModeEnabled`, `showCategorySearchBar`, `baseCurrency`, `categories` (query-filtered), `allCategories` (unfiltered, for parent selection), `reorderModalVisible`, `categoryModalData`, `sortOrder`, `sortModalVisible`.
- `CategoriesViewModel` — `@HiltViewModel`, `ComposeViewModel<CategoriesScreenState, CategoriesScreenEvent>`; injects `CategoryCreator`, `CategoryRepository`, `IvyWalletCtx`, `SharedPrefs`, `BaseCurrencyAct`, `AccountsAct`, `TrnsWithRangeAndAccFiltersAct`.
- `CategoryData(category, monthlyBalance, monthlyExpenses, monthlyIncome)` — `Reorderable` presentation row.
- `BoxWithConstraintsScope.CategoriesScreen(screen: CategoriesScreen)` — entry point; `UI(state, onEvent)` renderer; `CategoryCard`, `DefaultCategoryCard` private rows.
- `internal fun BoxWithConstraintsScope.CategoriesBottomBar(onClose: () -> Unit, ...)` — overlay bottom bar.

## Invariants
- `categories` is the search-filtered list; `allCategories` is the unfiltered list. Parent-category pickers must use `allCategories`, otherwise a search silently shrinks the selectable parents.
- `sortOrder` is persisted in `SharedPrefs` (`CATEGORY_SORT_ORDER` as `sortOrder.orderNum`) and reapplied on load; reordering writes `orderNum = index.toDouble()` back to every row.
- `compactCategoriesModeEnabled` / `showCategorySearchBar` are feature-flag driven (`Features.compactCategoriesMode`, `Features.showCategorySearchBar`).
- A `CategoryData` row's order identity is `category.orderNum` (see `CategoryData`), so sorting and manual reorder share one field.

## Side Effects
- Writes categories via `CategoryRepository.save` / `CategoryCreator` on create and reorder.
- Reads `SharedPrefs` (sort order, feature-independent layout flags) and writes the sort order on change.
- Monthly aggregation reads transactions through `TrnsWithRangeAndAccFiltersAct`, issued from `viewModelScope` coroutines.
- Navigates via `Navigation` (`nav.navigateTo`) into the category detail/edit flow.

## Verification
- Run: `./gradlew :feature:categories:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

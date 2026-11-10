# Budgets Package (com.ivy.budgets)

## Overview
Budget list and editor for the "budgets" screen: renders the budgets of the selected
time range with spent amounts, and hosts the create/edit modal. Budget persistence
goes through `BudgetCreator` and `WriteBudgetDao`; spending is recomputed from
transactions filtered by the budget's categories/accounts.

## Interface
- `BudgetScreenEvent` — sealed interface: `OnReorder(newOrder: List<DisplayBudget>)`, `OnCreateBudget(CreateBudgetData)`, `OnEditBudget(Budget)`, `OnDeleteBudget(Budget)`, `OnReorderModalVisible(Boolean)`, `OnBudgetModalData(BudgetModalData?)`.
- `BudgetScreenState` — `@Immutable`: `baseCurrency`, `budgets: ImmutableList<DisplayBudget>`, `categories`, `accounts`, `categoryBudgetsTotal`, `appBudgetMax`, `totalRemainingBudgetText`, `timeRange: FromToTimeRange?`, `reorderModalVisible`, `budgetModalData`.
- `BudgetViewModel` — `@HiltViewModel`, `ComposeViewModel<BudgetScreenState, BudgetScreenEvent>`; injects `SharedPrefs`, `WriteBudgetDao`, `BudgetCreator`, `IvyWalletCtx`, `AccountsAct`, `CategoryRepository`, `BudgetsAct`, `BaseCurrencyAct`.
- `BudgetModalData` — modal payload: `budget: Budget?`, `baseCurrency`, `categories`, `accounts`, `id: UUID`, `autoFocusKeyboard`.
- `BoxWithConstraintsScope.BudgetScreen(screen: BudgetScreen)` — screen entry point; `UI(state, onEvent)` is the stateless renderer.
- `BudgetModal(modal: BudgetModalData?, onCreate, ...)` — create/edit dialog, includes the category picker `CategoriesRow`.
- `internal fun BoxWithConstraintsScope.BudgetBottomBar(onClose: () -> Unit, ...)` — overlay bottom bar.
- `fun determineBudgetType(categoriesCount: Int): String` — resolves the budget-type label from the number of categories.
- `model/` — `DisplayBudget` view model type (see `./model/EGM.md`).

## Invariants
- `DisplayBudget` wraps the persisted `Budget` plus its computed `spentAmount`; the persisted order lives in `budget.orderId` and is only changed via `withNewOrderNum`.
- `BudgetModalData == null` means "no modal"; the modal is opened by setting it and closed by setting it to `null`.
- Reorder events carry the complete reordered list; the VM persists the new order in one pass.
- `totalRemainingBudgetText` is nullable — it stays `null` until the time range and budget totals are both resolved, and the toolbar then renders no figure.
- Amounts are always converted to `baseCurrency` before being added into `categoryBudgetsTotal`/`appBudgetMax`.

## Side Effects
- Writes budgets through `WriteBudgetDao`/`BudgetCreator` on create, edit, delete and reorder.
- Reads categories (`CategoryRepository.findAll()`), accounts and budgets inside `viewModelScope.launch` blocks.
- Reads `SharedPrefs` on start to resolve the start day of the month (`ivyContext.initStartDayOfMonthInMemory(sharedPrefs)`), which fixes the budget time range.

## Verification
- Run: `./gradlew :feature:budgets:assembleDebug` (this module has no `src/test` source set); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

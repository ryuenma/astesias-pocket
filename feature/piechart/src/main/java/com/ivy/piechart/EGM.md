# Pie Chart Package (com.ivy.piechart)

## Overview
Expenses/income breakdown screen: renders a custom pie chart of category amounts for
a period and account filter, with a bottom bar, month navigation and a per-category
transaction list. The aggregation itself lives in `action/PieChartAct`.

## Interface
- `CategoryAmount(category: Category?, amount: Double, associatedTransactions: List<Transaction> = emptyList(), isCategoryUnspecified: Boolean = false)` — `@Immutable` slice.
- `SelectedCategory(category: Category)` — `@Immutable`; `null` category means the unspecified slice (documented in the type).
- `PieChartStatisticEvent` — sealed interface: `OnStart(screen)`, `OnSelectNextMonth`, `OnSelectPreviousMonth`, `OnSetPeriod(timePeriod)`, `OnCategoryClicked(category?)`, `OnShowMonthModal(timePeriod?)`.
- `PieChartStatisticState` — `transactionType`, `period`, `baseCurrency`, `totalAmount`, `categoryAmounts`, `selectedCategory`, `accountIdFilterList`, `showCloseButtonOnly`, `filterExcluded`, `transactions`, `choosePeriodModal`.
- `PieChartStatisticViewModel` — `@HiltViewModel`, `ComposeViewModel<PieChartStatisticState, PieChartStatisticEvent>`; injects `SettingsDao`, `IvyWalletCtx`, `PieChartAct`, `SharedPrefs`, `TimeProvider`, `TimeConverter`.
- `PieChart(type, categoryAmounts, selectedCategory, modifier, onCategoryClick)` — Compose wrapper around the custom `class PieChartView(context) : View(context)`; hit-testing via `Zone(startAngle, endAngle, category)` and `getAngle(touchX, touchY, centerX, centerY)`.
- `BoxWithConstraintsScope.PieChartStatisticScreen(screen: PieChartStatisticScreen)`, private `UI`, `Header`.
- `BoxWithConstraintsScope.PieChartStatisticBottomBar(type, onClose, …)`.
- Subdirectory: `./action/EGM.md`.

## Invariants
- A `null` category in `CategoryAmount`/`SelectedCategory` is the "unspecified" slice — it is a real slice, not an absence.
- `CategoryAmount.amount` values are already converted to `baseCurrency`; the composable never converts.
- The selected category must be one of the rendered slices or the unspecified slice; the pie maps touch angle → zone before dispatching `OnCategoryClicked`.
- `filterExcluded` means the chosen account filter is an exclusion list, not an inclusion list.
- `treatTransferAsIncExp` is enabled only when the `TRANSFERS_AS_INCOME_EXPENSE` preference is on **and** `accountIdFilterList` is non-empty **and** the per-screen toggle is on; `showAccountTransfersCategory` then mirrors `accountIdFilterList.isNotEmpty()`.
- Period changes step exactly one month (`OnSelectNextMonth`/`OnSelectPreviousMonth`), and `OnSetPeriod` overrides that with an explicit value (also written back to `IvyWalletCtx`).
- `showCloseButtonOnly` hides the rest of the bottom bar when the screen is embedded in a tab.

## Side Effects
- Reads settings (`SettingsDao.findFirst()` for the base currency) and `SharedPrefs` (`TRANSFERS_AS_INCOME_EXPENSE`).
- Aggregates transactions through `PieChartAct(existingTransactions = …)` inside `viewModelScope` on `Dispatchers.Default`/`ioThread`.
- Writes the selected period into `IvyWalletCtx` (`updateSelectedPeriodInMemory`).

## Verification
- Run: `./gradlew :feature:piechart:assembleDebug` (this module has no `src/test` source set); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

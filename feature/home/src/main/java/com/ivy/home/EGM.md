# Home Package (com.ivy.home)

## Overview
The home tab: balance header with cash-flow figures, the transactions history list,
upcoming and overdue planned payments, the slide-out "more" menu and the customer
journey cards. It composes domain use-cases (`CalcWalletBalanceAct`,
`CalcIncomeExpenseAct`, `PlannedPaymentsLogic`) into one `HomeState`.

## Interface
- `HomeEvent` — sealed interface: `SetUpcomingExpanded`, `SetOverdueExpanded`, `BalanceClick`, `HiddenBalanceClick`, `HiddenIncomeClick`, `SetExpanded`, `SwitchTheme`, `SetBuffer(buffer)`, `SetCurrency(currency)`, `SetPeriod(period)`, `PayOrGetPlanned(transaction)`, `SkipPlanned(transaction)`, `SkipAllPlanned(transactions)`, `DismissCustomerJourneyCard(card)`, `SelectNextMonth`, `SelectPreviousMonth`.
- `HomeState` — `theme`, `name`, `period`, `baseData: AppBaseData`, `history: ImmutableList<TransactionHistoryItem>`, `stats: IncomeExpensePair`, `balance: BigDecimal`, `buffer: BufferInfo`, `upcoming`/`overdue: LegacyDueSection`, plus layout flags (`hideBalance`, `hideIncome`, `expanded`, …).
- `HomeViewModel` — `@HiltViewModel`, `ComposeViewModel<HomeState, HomeEvent>`; injects `IvyWalletCtx`, `Navigation`, `PlannedPaymentsLogic`, `CustomerJourneyCardsProvider`, `HistoryWithDateDivsAct`, `CalcIncomeExpenseAct`, `CalcWalletBalanceAct`, …
- `object Constants` — `SWIPE_HORIZONTAL_THRESHOLD = 200`, `SWIPE_DOWN_THRESHOLD_OPEN_MORE_MENU = 200`.
- `BoxWithConstraintsScope.HomeTab()` — tab entry point (hosts the VM).
- `BoxWithConstraintsScope.HomeUi(uiState: HomeState, onEvent: (HomeEvent) -> Unit)` — stateless renderer.
- `HomeLazyColumn(hideBalance, hideIncome, onSetExpand, ...)` — the main scroll container.
- `internal fun HomeHeader(...)`, `fun CashFlowInfo(...)`, `fun BoxWithConstraintsScope.MoreMenu(...)` — header and slide-out menu.
- Subdirectory: `./customerjourney/EGM.md`.

## Invariants
- The selected period is local `mutableStateOf(ivyContext.selectedPeriod)` and is written back to `IvyWalletCtx` (via `initSelectedPeriodInMemory`) only when the user changes it; month navigation steps exactly one month.
- `moreMenuExpanded` is held in `IvyWalletCtx` (`setMoreMenuExpanded`), so it survives tab switches and configuration changes.
- The transaction list scroll position is restored from `ivyContext.transactionsListState`; the tab must not reset it on recomposition.
- `Constants.SWIPE_HORIZONTAL_THRESHOLD` (200) and `SWIPE_DOWN_THRESHOLD_OPEN_MORE_MENU` (200) are the gesture thresholds — changing them changes month navigation and the more-menu gesture together.
- "Cash flow" figures come from `CalcIncomeExpenseAct` for the current period and are rendered as text only when `hideBalance`/`hideIncome` allow it; hiding never re-computes.
- `balance` is a `BigDecimal` (not `Double`) to keep currency arithmetic exact.

## Side Effects
- Reads transactions, accounts and settings through the injected legacy/domain use-cases on every `start`/`reload`.
- Writes `IvyWalletCtx` state: selected period, selected main tab (`selectMainTab`), more-menu expansion.
- Persists theme switching and buffer changes through the domain logic.
- Navigates through `Navigation` into search, pie chart and transaction screens.
- Schedules planned-payment actions (`PayOrGetPlanned`, `SkipPlanned`) which mutate transactions.

## Verification
- Run: `./gradlew :feature:home:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

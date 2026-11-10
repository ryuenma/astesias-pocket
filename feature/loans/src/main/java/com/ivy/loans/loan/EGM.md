# Loans List (com.ivy.loans.loan)

## Overview
The loans tab: two lists (pending and completed) with totals of what is owed to the
user and by the user, a create/edit loan modal, reorder support and a paid-off
visibility toggle.

## Interface
- `enum class LoanTab { PENDING, COMPLETED }` — `@Immutable`.
- `LoanScreenEvent` — sealed interface: `OnLoanCreate(CreateLoanData)`, `OnReordered(List<DisplayLoan>)`, `OnCreateAccount(CreateAccountData)`, `OnReOrderModalShow(Boolean)`, `OnTabChanged(LoanTab)`, `OnAddLoan`, `OnLoanModalDismiss`, `OnChangeDate`, `OnChangeTime`, `OnTogglePaidOffLoanVisibility`.
- `LoanScreenState` — `baseCurrency`, `completedLoans`, `pendingLoans`, `accounts`, `selectedAccount`, `loanModalData`, `reorderModalVisible`, `totalOweAmount`, `totalOwedAmount` (formatted strings), `paidOffLoanVisibility`, `dateTime`, `selectedTab`.
- `LoanViewModel` — `@HiltViewModel`, `ComposeViewModel<LoanScreenState, LoanScreenEvent>`; injects `LoanRecordDao`, `SettingsDao`, `LoanCreator`, `SharedPrefs`, `AccountCreator`, `LoanTransactionsLogic`, `LoansAct`, `AccountsAct`, `WriteLoanDao`, …
- `BoxWithConstraintsScope.LoansScreen(screen: LoansScreen)` — entry point; private `UI(state, onEventHandler)`, `Toolbar`, `LoanItem`.
- `internal fun BoxWithConstraintsScope.LoanBottomBar(tab: LoanTab, selectTab: (LoanTab) -> Unit, …)`.
- `internal object Constants` — `SWIPE_HORIZONTAL_THRESHOLD = 200`.
- Subdirectory: `./data/EGM.md`.

## Invariants
- `selectedTab` defaults to `LoanTab.PENDING`; `paidOffLoanVisibility` defaults to `true`, so completed loans are visible until the user hides them.
- `pendingLoans` and `completedLoans` are both derived from one `allLoans` list; the tab only chooses which is rendered.
- `totalOweAmount` / `totalOwedAmount` are pre-formatted strings in `defaultCurrencyCode`, computed when state is built — they are never formatted in the composable.
- Reordering operates on `DisplayLoan`, whose order identity is `Loan.orderNum` (see `data/DisplayLoan.kt`).
- `Constants.SWIPE_HORIZONTAL_THRESHOLD` (200 px) is the gesture threshold for switching tabs.

## Side Effects
- Reads loans and loan records (`LoanRecordDao`), settings (`SettingsDao`) and accounts (`AccountsAct`).
- Writes loans through `LoanCreator` / `WriteLoanDao`, and creates accounts through `AccountCreator` when the modal asks for one.
- Records loan transactions via `LoanTransactionsLogic` (ledger writes) when a loan is created or paid.
- Persists reorder state implicitly through `DisplayLoan.withNewOrderNum`.

## Verification
- Run: `./gradlew :feature:loans:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

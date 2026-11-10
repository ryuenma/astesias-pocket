# Planned Payments List (com.ivy.planned.list)

## Overview
The planned-payments tab: one-time and recurring rules with their income/expense
totals, expandable sections, paid/unpaid cards, and the pending-bill inbox that lets
the user adjust a bill's amount or dismiss it.

## Interface
- `BoxWithConstraintsScope.PlannedPaymentsScreen(screen: PlannedPaymentsScreen)` — entry point; private `UI(state, onEvent)`.
- `PlannedPaymentsScreenState` — `currency`, `categories`, `accounts`, `oneTimePlannedPayment`, `oneTimeIncome`, `oneTimeExpenses`, `recurringPlannedPayment`, `recurringIncome`, `recurringExpenses`, `isOneTimePaymentsExpanded`, `isRecurringPaymentsExpanded`, `pendingBills`.
- `PlannedPaymentsScreenEvent` — sealed interface: `OnOneTimePaymentsExpanded`, `OnRecurringPaymentsExpanded`, `OnPendingBillAmountChanged(occurrenceKey, amount)`, `OnPendingBillDismissed(occurrenceKey)`.
- `PlannedPaymentsViewModel` — `@HiltViewModel`, `ComposeViewModel<PlannedPaymentsScreenState, PlannedPaymentsScreenEvent>`; injects `SettingsDao`, `PlannedPaymentsLogic`, `CategoryRepository`, `AccountsAct`, `BillReminderInbox`, `TimeProvider`.
- `PlannedPaymentsLazyColumn(Header, …)` — the list container; private `plannedPaymentItems`, `onPlannedPaymentClick`, `NoPlannedPaymentsEmptyState`.
- `LazyItemScope.PlannedPaymentCard(baseCurrency, categories, accounts, plannedPayment, onClick, …)`.
- `fun LazyListScope.pendingBillItems(bills, currency, zone, onAdjustAmount, …)` and private `PendingBillCard`.
- `BoxWithConstraintsScope.PlannedPaymentsBottomBar(bottomInset, onClose, …)`.

## Invariants
- Pending bills are keyed by `occurrenceKey` (a `String`), not by row id: adjusting an amount and dismissing a bill both address the same occurrence through that key.
- Income and expense totals are computed separately for one-time and recurring sections; a rule contributes to exactly one section.
- Section expansion flags live in the ViewModel, so collapse state survives recomposition but not process death.
- `PendingBillCard` formats due dates against the passed `ZoneId`; the composable must not use the system default zone.
- Clicking a rule navigates to the edit screen through the `Navigation` passed into `PlannedPaymentsLazyColumn`.

## Side Effects
- Reads settings (`SettingsDao.findFirst()` for the currency), categories and accounts on start.
- Writes pending-bill decisions through `BillReminderInbox` (adjust amount / dismiss) from `viewModelScope`.
- Navigates into `EditPlannedScreen` on card click.

## Verification
- Run: `./gradlew :feature:planned-payments:assembleDebug` (this module has no `src/test` source set).

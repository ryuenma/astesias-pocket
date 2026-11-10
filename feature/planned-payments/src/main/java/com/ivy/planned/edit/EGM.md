# Edit Planned Payment (com.ivy.planned.edit)

## Overview
Create/edit screen for a planned-payment rule: amount, type, account, category,
recurrence and an optional bill-reminder day of month. Saving writes the rule and
regenerates its pending occurrences; the reminder is rescheduled with it.

## Interface
- `BoxWithConstraintsScope.EditPlannedScreen(screen: EditPlannedScreen)` — entry point; private `UI(screen, state, onEvent)`.
- `EditPlannedScreenState` — `currency`, `transactionType`, `startDate`, `intervalN`, `intervalType`, `oneTime`, `dueDayOfMonth: Int?` (1..31, `null` = no monthly reminder), `initialTitle`, `description`, `categories`, `accounts`, `account`, `category`, `amount`, modal visibilities, `categoryModalData`, `accountModalData`, `recurringRuleModalData`.
- `EditPlannedScreenEvent` — sealed interface: `OnRuleChanged(startDate, oneTime, intervalN, intervalType)`, `OnAmountChanged`, `OnTitleChanged`, `OnDescriptionChanged`, `OnCategoryChanged`, `OnAccountChanged`, `OnSetTransactionType`, `OnSave(closeScreen)`, `OnDelete`, `OnEditCategory`, `OnCreateCategory`, `OnCreateAccount`, and the modal-visibility events (`OnCategoryModalVisible`, `OnDescriptionModalVisible`, `OnDeleteTransactionModalVisible`, `OnAmountModalVisible`, `OnTransactionTypeModalVisible`, `OnCategoryModalDataChanged`, `OnRecurringRuleModalDataChanged`, `OnAccountModalDataChanged`, `OnDueDayOfMonthChanged`).
- `EditPlannedViewModel` — `@HiltViewModel`, `ComposeViewModel<EditPlannedScreenState, EditPlannedScreenEvent>`; injects `AccountDao`, `CategoryRepository`, `SettingsDao`, `Navigation`, `PlannedPaymentRuleDao`, `PlannedPaymentsGenerator`, `BillReminderScheduler`, …
- `RecurringRule(startDate, intervalN, intervalType, oneTime, onShowRecurringRuleModal, …)`, `fun hasRecurringRule(startDate, intervalN, intervalType, oneTime): Boolean`.
- `BillReminderDayPicker(dueDayOfMonth: Int?, enabled: Boolean, onDueDayOfMonthChanged: (Int?) -> Unit, …)`.

## Invariants
- `oneTime = true` and a recurrence (`intervalN`/`intervalType`) are mutually exclusive: a one-time rule carries no interval, and `hasRecurringRule` is the single predicate that decides which is shown.
- `dueDayOfMonth` is `null` (no reminder) or an `Int` in 1..31; the picker cannot produce a value outside that range.
- Saving regenerates pending occurrences through `PlannedPaymentsGenerator`, so editing an existing rule must not leave stale generated transactions behind.
- `OnSave(closeScreen = true)` is the default: only intermediate saves pass `false`.
- Category and account creation from inside the modal go through `CreateCategoryData`/`CreateAccountData` events rather than writing directly, so the rule keeps its in-memory selection consistent.

## Side Effects
- Writes rules via `PlannedPaymentRuleDao` and regenerates occurrences with `PlannedPaymentsGenerator`.
- Schedules/cancels bill reminders with `BillReminderScheduler`.
- Reads settings (`SettingsDao`) for the base currency and `CategoryRepository`/`AccountDao` for pickers.
- Navigates away on save/delete through `Navigation`.

## Verification
- Run: `./gradlew :feature:planned-payments:assembleDebug` (this module has no `src/test` source set).

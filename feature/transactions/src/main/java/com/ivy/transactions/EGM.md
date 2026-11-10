# Transactions Package (com.ivy.transactions)

## Overview
The account/category transaction list screen: history grouped by date, upcoming and
overdue planned transactions, period navigation and the delete/skip/pay actions for
single rows and in bulk.

## Interface
- `TransactionsEvent` — sealed interface: `SetUpcomingExpanded`, `SetOverdueExpanded`, `SetPeriod(screen, period)`, `NextMonth(screen)`, `PreviousMonth(screen)`, `Delete(screen)`, `EditCategory(updatedCategory)`, `EditAccount(screen, account, newBalance)`, `PayOrGet(screen, transaction)`, `SkipTransaction(screen, transaction)`, `SkipTransactions(screen, transactions)`, `UpdateAccountDeletionState(confirmationText)`, `SetSkipAllModalVisible(visible)`, `OnDeleteModal1Visible(delete)`, `OnChoosePeriodModalData(data)`.
- `TransactionsState` — `period`, `baseCurrency`, `currency`, `categories`, `accounts`, `account`, `category`, `balance`, `balanceBaseCurrency`, `income`, `expenses`, `initWithTransactions`, `treatTransfersAsIncomeExpense`, `upcoming*`/`overdue*` sections and totals, `enableDeletionButton`, `skipAllModalVisible`, `deleteModal1Visible`.
- `TransactionsViewModel` — `@HiltViewModel`, `ComposeViewModel<TransactionsState, TransactionsEvent>`; injects `AccountRepository`, `AccountDao`, `CategoryRepository`, `IvyWalletCtx`, `Navigation`, `WalletAccountLogic`, `WalletCategoryLogic`, `CategoryCreator`, …
- `BoxWithConstraintsScope.TransactionsScreen(screen: TransactionsScreen)` — entry point; private `UI(screen, period, baseCurrency, currency, skipAllModalVisible, …)`.

## Invariants
- Mutating events that act on a scoped list carry the originating `screen` (which account/category the list is scoped to) so a delayed event cannot act on the wrong list; the exception is `EditCategory`, which edits the category entity itself.
- `period` is a `mutableStateOf(ivyContext.selectedPeriod)`; month navigation steps one month and keeps upcoming/overdue in sync with it.
- Fixed-account screens (`screen.accountId != null`) derive `balance`/`balanceBaseCurrency` from that account; `balanceBaseCurrency` is nullable because conversion is not always possible.
- Deletion is guarded: `enableDeletionButton` is false until `UpdateAccountDeletionState` confirms, and account deletion additionally requires the confirmation text to match.
- `treatTransfersAsIncomeExpense` changes what `income`/`expenses` mean for transfers; the flag is read from settings, not decided per screen.
- `initWithTransactions` distinguishes "list is ready" from "empty list".

## Side Effects
- Reads accounts, categories and transactions from repositories/DAOs inside `viewModelScope`.
- Writes: deletes transactions, edits categories and accounts (`WalletAccountLogic`/`WalletCategoryLogic`), pays/skips planned transactions (ledger writes).
- Reads the theme from `IvyWalletCtx` (`ivyContext.theme == Theme.LIGHT`) for row contrast colours.
- Navigates through `Navigation` (edit transaction, month modal, skip-all modal).

## Verification
- Run: `./gradlew :feature:transactions:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

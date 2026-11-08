# Edit Transaction Package (com.ivy.transaction)

## Overview
The transaction editor: creates and edits income, expense and transfer
transactions, including cross-profile transfers that are labelled against another
profile's account (`DirectoryAccountEntity`). The ViewModel is the largest in the
`feature/` tree and owns every field of the form; the screen is a thin renderer
plus the amount/date/category modals.

## Interface
- `EditTransactionScreen(screen: EditTransactionScreen)` — screen entry point. `screen.initialTransactionId == null` means create mode, otherwise edit mode.
- `EditTransactionViewState` — `transactionType`, `initialTitle`, `titleSuggestions`, `currency`, `description`, `dateTime`, `dueDate`, `accounts`, `categories`, `account`, `toAccount`, `category`, `amount`, … (one property per form field).
- `EditTransactionViewEvent` — sealed interface: field events (`OnAmountChanged`, `OnTitleChanged`, `OnDescriptionChanged`, `OnCategoryChanged`, `OnAccountChanged`, `OnToAccountChanged`, `OnToDirectoryAccountChanged`, `OnClearDirectoryDestination`, `OnDueDateChanged`, `OnChangeDate`, `OnChangeTime`, `OnSetTransactionType`), actions (`OnPayPlannedPayment`, `Delete`, `Duplicate`, `Save(closeScreen)`, `SetHasChanges`, `UpdateExchangeRate`), entity creation (`CreateCategory`, `EditCategory`, `CreateAccount`) and the nested `TagEvent` family (`SaveTag`, `OnTagSelect`, `OnTagDeSelect`, `OnTagDelete`, `OnTagSearch`, `OnTagEdit`).
- `EditTransactionViewModel` — `@HiltViewModel`, `ComposeViewModel<EditTransactionViewState, EditTransactionViewEvent>`; injects `Context`, `Toaster`, `DirectoryRepository`, `DirectorySyncRepository`, `CrossProfileTransferPublisher`, `WriteTransactionDao`, `LoanDao`, `SettingsDao`, `Navigation`, `SharedPrefs`, `ExchangeRatesLogic`, `CategoryCreator`, `AccountCreator`, `PlannedPaymentsLogic`, `SmartTitleSuggestionsLogic`, `LoanTransactionsLogic`, `AccountsAct`, `CategoryRepository`, `TrnByIdAct`, `AccountByIdAct`, `TransactionRepository`, `TransactionMapper`, `TagRepository`, `TagMapper`, `Features`, `TimeConverter`, `TimeProvider`, `DateTimePicker`.
- `start(screen)` — loads the transaction (edit mode) or seeds defaults (create mode); closes the screen if no accounts exist.
- `loadDirectory()` — two-phase load of other profiles' accounts (cached read, then refresh).
- Subdirectories: `./answers/EGM.md`, `./directory/EGM.md`, `./pending/EGM.md`.

## Invariants
- **Time of day defaults to noon on edit, not on create.** `DEFAULT_TIME_OF_DAY = LocalTime.of(12, 0)` is only applied in `handleChangeDate`; the create path stamps `utcNow()`. The stored value is a UTC instant, and local midnight at UTC+7 lands on the previous UTC day, shifting the transaction's date. Noon keeps the instant inside the same UTC day for offsets −11…+12.
- Create mode is detected solely by `screen.initialTransactionId == null`; edit mode loads the existing transaction and its associated tags.
- **The editor does not edit cross-profile transfers.** `display()` never restores the directory destination from `counterpartProfileId`, so both sides are null on load and every save is refused.
- The directory picker must never block on the network: `loadDirectory()` first publishes cached rows, then refreshes; a failed refresh leaves the last known list rather than a spinner.
- After a refresh, the list is re-read from the repository (`directoryRepository.others()`), never taken from the sync result, because the query excluding this profile's own accounts is the only correct filter.
- A ledger write for a cross-profile transfer happens **before** the transfer is queued for publication: the money already moved, so publishing must not gate recording it.
- Ordinary transfers between two of the profile's own accounts are never published.
- **Retries are not idempotent on the edit path.** `legId` and `correlationId` are two independent random UUIDs; the idempotency gate sits in a function that is never reached on the edit path.
- `SetHasChanges` drives the unsaved-changes guard; `Save(closeScreen)` decides whether the screen pops after persisting.

## Side Effects
- Writes transactions through `WriteTransactionDao` / `TransactionRepository` (plus `TransactionMapper` for domain conversion).
- Queues cross-profile transfers via `CrossProfileTransferPublisher.publishRecorded(...)` after the ledger write; `legId` and `correlationId` are two independent random UUIDs (not the same), so retries are **not** idempotent on this path.
- Refreshes the directory through `DirectorySyncRepository.refresh()` (network) from `viewModelScope`.
- Reads settings (`SettingsDao`) for the base currency, `SharedPrefs` for flags, and `Features` for gated behaviour.
- Debounces tag search by 500 ms (`tagSearchDebounceTimeInMills`) against `TagRepository`.
- Shows user-facing errors through `Toaster`; navigates with `Navigation`.

## Verification
- Run: `./gradlew :feature:edit-transaction:testDebugUnitTest` (unit tests in `src/test`); integration-test runner is applied through `ivy.integration.testing`.

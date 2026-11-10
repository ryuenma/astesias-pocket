# Main Package (com.ivy.main)

## Overview
The app shell: hosts the selected main tab (HOME / ACCOUNTS), the animated bottom
bar with the add-transaction buttons, and one-time start-up work — exchange-rate
sync and publishing this device's accounts to the transfer directory.

## Interface
- `BoxWithConstraintsScope.MainScreen(screen: MainScreen)` — shell entry point; observes `MainViewModel.currency`, calls `onScreenStart { viewModel.start(screen) }`.
- `BoxWithConstraintsScope.UI(screen, tab: MainTab, baseCurrency, selectTab, …)` — private renderer that switches between tabs.
- `BoxWithConstraintsScope.BottomBar(tab: MainTab, selectTab: (MainTab) -> Unit, …)` — animated bottom bar; private `TransactionButtons(buttonsShownPercent, fabStartX, fabStartY, onAddIncome, …)`.
- `MainViewModel` — `@HiltViewModel`, plain `ViewModel`; injects `IvyWalletCtx`, `Navigation`, `SyncExchangeRatesUseCase`, `AccountCreator`, `SharedPrefs`, `CurrencyRepository`, `TransferSyncRunner`; exposes `currency: LiveData<String>`.
- `start(screen)` — installs the back handler, loads the base currency, restores `dataBackupCompleted`, syncs exchange rates, runs `transferSyncRunner.runOnce()`.
- `selectTab(tab)`, `createAccount(data)`.

## Invariants
- Back on the shell never exits from the accounts tab: if `ivyContext.mainTab == MainTab.ACCOUNTS` the handler switches to `MainTab.HOME` and returns `true` (consumed); otherwise it returns `false` so the back stack closes the app.
- `start(screen)` is idempotent in effect: the back handler is overwritten each time and the sync work is safe to repeat.
- The account publish pass runs on **every** app start (`transferSyncRunner.runOnce()`), outside the `ioThread` block used for exchange rates, so it never delays them and successfully publishes accounts for installs that already existed before the feature.
- `TestIdlingResource.increment()` / `decrement()` bracket the async start-up work, so instrumentation tests wait for it.
- The tab selection lives in `IvyWalletCtx`, not in the composable — tab survives configuration changes.

## Side Effects
- Network: `syncExchangeRatesUseCase.sync(baseCurrency)` and `transferSyncRunner.runOnce()` (publishes accounts to the other profile).
- Reads `SharedPrefs` (`DATA_BACKUP_COMPLETED`) and `CurrencyRepository.getBaseCurrency()`.
- Writes `IvyWalletCtx` (selected tab, backup-completed flag) and registers `nav.onBackPressed[screen]`.
- Creates accounts through `AccountCreator` when the shell asks for one.

## Verification
- Run: `./gradlew :feature:main:assembleDebug` (this module has no `src/test` source set).

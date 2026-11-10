# Balance Package (com.ivy.balance)

## Overview
Modal "balance" screen: shows the wallet balance for the selected period plus the
balance that would remain after all planned payments. It is a small read-mostly
screen whose only state change is moving the time period forward/backward.

## Interface
- `BalanceEvent` — sealed interface: `OnSetPeriod(timePeriod: TimePeriod)`, `OnNextMonth`, `OnPreviousMonth`.
- `BalanceState` — `@Immutable`: `period: TimePeriod`, `baseCurrencyCode`, `currentBalance`, `plannedPaymentsAmount`, `balanceAfterPlannedPayments`.
- `BalanceViewModel` — `@HiltViewModel`, `ComposeViewModel<BalanceState, BalanceEvent>`; injects `PlannedPaymentsLogic`, `IvyWalletCtx`, `BaseCurrencyAct`, `CalcWalletBalanceAct`, `TimeProvider`, `TimeConverter`.
- `BoxWithConstraintsScope.BalanceScreen(screen: BalanceScreen)` — screen entry point (nav screen type from `:shared:ui:navigation`).
- Private UI: `UI`, `CurrentBalance`, `BalanceAfterPlannedPayments`, `CloseButton`; previews `Preview`, `BalanceScreenUiTest`.

## Invariants
- The period starts at `ivyContext.selectedPeriod` and is held as local `mutableStateOf`; the screen does not write the selected period back to `IvyWalletCtx`.
- `balanceAfterPlannedPayments = currentBalance + plannedPaymentsAmount`: `plannedPaymentsAmount` is signed (income adds, expenses subtract) and is scaled by the number of months ahead of the period, so it must never be subtracted.
- Next/previous month steps the period by exactly one month via `TimeConverter`; no free-form date picking.

## Side Effects
- Reads `CalcWalletBalanceAct` and `PlannedPaymentsLogic` (database/domain reads) on every period change.
- Reads the base currency from `BaseCurrencyAct`; no writes to storage.
- `CloseButton` pops the screen through `Navigation`.

## Verification
- Run: `./gradlew :feature:balance:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

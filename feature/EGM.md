# feature/ Blueprint

## Overview

Gradle sub-project group holding the 18 feature modules that render and drive the
app's screens. Each child is an Android library module applying the `ivy.feature`
convention (Compose + Paparazzi) and owns one vertical slice of the UI: its
ViewModels, its screens, and the Compose state they read.

## Subdirectories & Boundaries

- accounts: [./accounts/EGM.md](./accounts/EGM.md) - Wallet account list and total-balance header (`:feature:accounts`).
- attributions: [./attributions/EGM.md](./attributions/EGM.md) - Open-source attributions screen (`:feature:attributions`).
- balance: [./balance/EGM.md](./balance/EGM.md) - Balance modal with planned-payment impact (`:feature:balance`).
- budgets: [./budgets/EGM.md](./budgets/EGM.md) - Budgets list and editor (`:feature:budgets`).
- categories: [./categories/EGM.md](./categories/EGM.md) - Categories list with search, sort and reorder (`:feature:categories`).
- edit-transaction: [./edit-transaction/EGM.md](./edit-transaction/EGM.md) - Transaction editor (`:feature:edit-transaction`).
- features: [./features/EGM.md](./features/EGM.md) - Internal feature-flag screen (`:feature:features`).
- home: [./home/EGM.md](./home/EGM.md) - Home tab (`:feature:home`).
- import-data: [./import-data/EGM.md](./import-data/EGM.md) - CSV and backup import flow (`:feature:import-data`).
- loans: [./loans/EGM.md](./loans/EGM.md) - Loans list and loan details (`:feature:loans`).
- main: [./main/EGM.md](./main/EGM.md) - App shell (`:feature:main`).
- onboarding: [./onboarding/EGM.md](./onboarding/EGM.md) - First-run onboarding (`:feature:onboarding`).
- piechart: [./piechart/EGM.md](./piechart/EGM.md) - Pie chart statistics (`:feature:piechart`).
- planned-payments: [./planned-payments/EGM.md](./planned-payments/EGM.md) - Planned payments list and editor (`:feature:planned-payments`).
- reports: [./reports/EGM.md](./reports/EGM.md) - Filtered reports with export (`:feature:reports`).
- search: [./search/EGM.md](./search/EGM.md) - Transaction search (`:feature:search`).
- settings: [./settings/EGM.md](./settings/EGM.md) - Settings screen (`:feature:settings`).
- transactions: [./transactions/EGM.md](./transactions/EGM.md) - Transaction list per account/category (`:feature:transactions`).

## Global Invariants

- Each module applies the `ivy.feature` plugin (`ivy.module` + `ivy.compose` + `ivy.paparazzi`),
  which fixes the Compose/Paparazzi toolchain. `ivy.integration.testing` is added only by
  `:feature:accounts` and `:feature:edit-transaction`.
- Screen entry points are `BoxWithConstraintsScope` extensions named after their navigation
  screen type and receive that `screen` object as their argument (e.g. `BudgetScreen(screen)`);
  the tab-level composables (`AccountsTab()`, `HomeTab()`) take no argument because they are
  invoked directly by `:feature:main` or by the shell's tab switch.
- State is exposed as an `@Immutable` data class built inside `ComposeViewModel.uiState()`;
  composables render state and raise events, and never mutate ViewModel fields directly.
- A module's package does not always match its Gradle name: `:feature:edit-transaction` ships
  `com.ivy.transaction`, `:feature:import-data` ships `com.ivy.importdata`, and
  `:feature:planned-payments` ships `com.ivy.planned`.
- Contracts in this tree use the filename `EGM.md` so that upstream Ivy `README.md` files stay untouched.

## Global Side Effects

- All persistence goes through `:shared:data:core` repositories/DAOs and the legacy
  `:temp:legacy-code` logic; feature modules never open the database themselves.
- Preferences are read and written through `SharedPrefs` and `Features`; cross-screen UI state
  (selected tab, selected period, screen size, more-menu expansion) lives in `IvyWalletCtx`.
- Network work is confined to sync paths (exchange rates, cross-profile transfer directory and
  transfer events) and is always best-effort, so a dead server degrades to cached data.
- `build/` directories under each module are Gradle output and are deliberately not contracted.

## System Verification

- Run: `./gradlew test` (all module unit tests) and `./gradlew :app:assembleDebug` (full build).

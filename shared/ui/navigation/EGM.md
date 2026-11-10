# shared/ui/navigation — screen graph

## Overview

Android library module (namespace `com.ivy.navigation`) that owns the app's navigation model: a
`Screen` marker sealed interface, the concrete `Screens` set, a `Singleton` `Navigation` object
holding the back stack, and the `NavigationRoot` composable that renders the current screen and
scopes ViewModels to it.

`build.gradle.kts`: `implementation(projects.shared.base)`,
`implementation(projects.shared.domain)`, `implementation(projects.shared.ui.core)`.

## Interface

- `Navigation` — `navigateTo`, `back`, `backStackEmpty`, `resetBackStack`, `currentScreen`,
  `lastScreen`; deprecated legacy members `modalBackHandling`, `onBackPressed`, `lastModalBackHandlerId`.
- `Screen` — `sealed interface` with `val isLegacy: Boolean get() = false`.
- `Screens.kt` — `MainScreen`, `OnboardingScreen`, `EditTransactionScreen`, `TransactionsScreen`,
  `PieChartStatisticScreen`, `EditPlannedScreen`, `BalanceScreen`, `PlannedPaymentsScreen`,
  `CategoriesScreen`, `SettingsScreen`, `PendingTransfersScreen`, `TransferAnswersScreen`,
  `ReportScreen`, `BudgetScreen`, `LoansScreen`, `SearchScreen`, `LoanDetailsScreen`,
  `FeaturesScreen`, `AttributionsScreen`, `ImportScreen`, `CSVScreen`.
- `NavigationRoot(navigation, navGraph)`, `@Composable fun navigation()`, `screenScopedViewModel<T>()`,
  `@Composable fun IvyPreview(dark, content)`.
- `src/` → [./src/EGM.md](./src/EGM.md)

## Subdirectories & Boundaries

- [src/](./src/EGM.md) — Source-set container for :shared:ui:navigation; main only, no tests.

## Invariants

- `Navigation` is `@Singleton` **and** `@Stable`; its `currentScreen` is a private-set
  `mutableStateOf`, so it must only change through `navigateTo` / `back` / `resetBackStack`.
  Mutating it externally would desynchronise `backStack` from `lastScreen`.
- The back stack is a `java.util.Stack<Screen>`; `navigateTo` pushes `lastScreen` (not
  `currentScreen`) and then switches. `back()` returns `false` when empty — callers must honour
  the boolean or the app exits on the first back press.
- `Screen.isLegacy` defaults to `false` and exists **only** so `NavigationRoot` can decide whether to
  clear the ViewModel store. New Material 3 screens must not override it.
- `NavigationRoot` clears `viewModelStore` only when the *disposed* screen was non-legacy; legacy
  screens deliberately leak their ViewModels so the old Surface-based screens keep working.
- This module may depend on `ui/core`, `domain` and `base` — never on `feature/*` or `app`. Screens
  are described as data; the mapping to composables lives in `app/`.

## Side Effects

- Mutates the singleton back stack and the Compose snapshot state `currentScreen`.
- `NavigationRoot`'s `DisposableEffect` clears the current `ViewModelStore` on screen disposal.
- `navigation()` fails fast with `error("No LocalNavigation")` outside `NavigationRoot`.

## Verification

- Run: `./gradlew :shared:ui:navigation:testDebugUnitTest`

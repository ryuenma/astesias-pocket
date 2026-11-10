# com.ivy.navigation — navigation implementation

## Overview

The whole navigation model in five files: the `Screen` marker, the concrete screen list, the
`Navigation` back stack, the `NavigationRoot` host, and a preview helper.

## Interface

- `sealed interface Screen { val isLegacy: Boolean get() = false }`
- `@Stable @Singleton class Navigation @Inject constructor()` with `currentScreen: Screen?`,
  `lastScreen: Screen?`, `navigateTo(screen)`, `back(): Boolean`, `backStackEmpty()`,
  `resetBackStack()`, and deprecated `modalBackHandling`, `onBackPressed`, `lastModalBackHandlerId()`,
  `data class ModalBackHandler(id: UUID, onBackPressed: () -> Boolean)`.
- `Screens.kt` — `data object` screens: `MainScreen`, `OnboardingScreen`, `BalanceScreen`,
  `PlannedPaymentsScreen`, `CategoriesScreen`, `SettingsScreen`, `PendingTransfersScreen`,
  `TransferAnswersScreen`, `ReportScreen`, `BudgetScreen`, `LoansScreen`, `SearchScreen`,
  `FeaturesScreen`, `AttributionsScreen`; `data class` screens with arguments:
  `EditTransactionScreen`, `TransactionsScreen`, `PieChartStatisticScreen`, `EditPlannedScreen`,
  `LoanDetailsScreen`, `ImportScreen`, `CSVScreen`, plus `data object FeaturesScreen`/
  `AttributionsScreen` variants of the same sealed hierarchy.
- `@Composable fun NavigationRoot(navigation: Navigation, navGraph: @Composable (screen: Screen?) -> Unit)`
- `@Composable fun navigation(): Navigation` — reads the private `LocalNavigation` composition local.
- `inline @Composable fun <reified T : ViewModel> screenScopedViewModel(factory: ViewModelProvider.Factory? = null): T`
- `@Composable fun IvyPreview(dark: Boolean = false, content: @Composable () -> Unit)`

## Invariants

- `LocalNavigation` is `private` and created with a throwing default
  (`error("No LocalNavigation")`): calling `navigation()` outside `NavigationRoot` fails immediately
  instead of returning a stale graph.
- `navGraph` receives `Screen?` — the null state is legal and means "no screen yet"; the graph must
  render something for it rather than force-unwrapping.
- Screens carrying arguments are `data class`es: equality drives `currentScreen` comparison and
  `onBackPressed` map lookups. A screen that is not a `data class` breaks both.
- `IvyPreview` constructs a throwaway `Navigation()` and wraps content in `IvyMaterial3Theme`, so
  previews never touch the singleton graph; it is the correct preview entry point instead of
  hand-rolling `NavigationRoot` + theme in each feature.
- Deprecated members (`modalBackHandling`, `onBackPressed`, `ModalBackHandler`) must not be used by
  new code — Compose `BackHandler {}` replaces them.

## Side Effects

- `Navigation.navigateTo/back/resetBackStack` mutate the singleton back stack and the Compose state
  `currentScreen`; `screenScopedViewModel` obtains (and therefore creates) ViewModels on the current
  `ViewModelStoreOwner`.

## Verification

- Run: `./gradlew :shared:ui:navigation:testDebugUnitTest`

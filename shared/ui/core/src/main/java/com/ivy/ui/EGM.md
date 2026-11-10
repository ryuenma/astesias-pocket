# com.ivy.ui — shared UI logic

## Overview

The non-theme half of shared UI: a Compose-native ViewModel base class, the money formatter, a
scroll-position utility, and three subpackages (components, DI, time). These types are consumed by
every feature screen, so their signatures are effectively app-wide API.

## Interface

- `@Stable abstract class ComposeViewModel<UiState, UiEvent> : ViewModel()`
  - `@Composable abstract fun uiState(): UiState`
  - `abstract fun onEvent(event: UiEvent)`
- `class FormatMoneyUseCase @Inject constructor(Features, DevicePreferences, @ApplicationContext Context)`
  - `suspend fun format(value: Double, shortenAmount: Boolean): String`
  - `const val THOUSAND / MILLION / BILLION / TRILLION`
- `@Composable fun rememberScrollPositionListState(key: String, params: String = "", initialFirstVisibleItemIndex: Int = 0,
  initialFirstVisibleItemScrollOffset: Int = 0): LazyListState`

## Subdirectories & Boundaries

- [component/](./component/EGM.md) — Shared stateless composables: BackButton and OpenSourceCard (opens the Ivy Wallet GitHub URL).
- [di/](./di/EGM.md) — Hilt @Binds module for the UI layer: TimeFormatter, DevicePreferences and DateTimePicker.
- [time/](./time/EGM.md) — Time formatting contracts: DevicePreferences (locale, 24h) and TimeFormatter with LocalDateTime/LocalTime/Instant format extensions and a Style sealed hierarchy.

## Invariants

- `ComposeViewModel.uiState()` is `@Composable`, so state must be built from Compose primitives and
  immutable structures — a `mutableStateOf` returned from it would mutate during composition.
- `FormatMoneyUseCase` captures `locale` **once in the constructor** and builds its three
  `DecimalFormat`s from it. A locale change at runtime does not affect an existing instance; a
  formatter must be re-created (or the case re-injected) to pick it up.
- The shortening suffixes are hard-coded Indonesian: `rb` (ribu, 10³), `jt` (juta, 10⁶), `M`
  (miliar, 10⁹), `T` (triliun, 10¹²). They are **not** localized strings — do not "fix" them to
  K/M/B without a product decision, and do not move them into `strings.xml` alone.
- Shortening only applies when `shortenAmount` is true **and** `|value| >= THOUSAND`; below that the
  full format is used with a decimal point only if `Features.showDecimalNumber` is enabled.
- `rememberScrollPositionListState` keys its cache by `(key, params)`; a changed `params` discards
  the remembered position by design.

## Side Effects

- `FormatMoneyUseCase.format` reads a feature flag via `Features.showDecimalNumber.isEnabled(context)`
  on every non-shortened call (DataStore/SharedPreferences read).
- `rememberScrollPositionListState` writes to a **file-private static** `saveMap` on dispose and
  never clears it: entries accumulate for the process lifetime and survive navigation (that is the
  feature), but they are not `rememberSaveable`-persisted across process death.
- Reads `Locale.getDefault()` and `DateFormat.is24HourFormat` through `DevicePreferences`.

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`

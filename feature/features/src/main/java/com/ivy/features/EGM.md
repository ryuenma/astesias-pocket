# Features Package (com.ivy.features)

## Overview
Internal "experimental features" screen: lists every `Features` toggle that is
user-exposed, grouped by section, and flips them at runtime. Toggling writes the
flag and immediately rebuilds the UI state, so no restart is needed.

## Interface
- `FeaturesUiEvent` — sealed interface: `ToggleFeature(key: String)`.
- `FeaturesUiState` — `featureItemViewStates: ImmutableList<FeatureItemViewState>`.
- `FeatureItemViewState` — sealed interface: `FeatureToggleViewState(key, name, enabled, description?)`, `FeatureHeaderViewState(name)`.
- `FeaturesViewModel` — `@HiltViewModel`, `ComposeViewModel<FeaturesUiState, FeaturesUiEvent>`; injects `Features` and `@ApplicationContext Context`.
- `FeaturesScreenImpl(viewModel: FeaturesViewModel = screenScopedViewModel())` — screen entry point.
- Private UI: `FeaturesUi`, `BackButton`, `Title`, `Content`, `FeatureRow`, `FeatureSectionDivider`.

## Invariants
- The screen owns no state of its own: `enabled` always reflects the `Features` provider after a toggle, never an optimistic local copy.
- `FeatureHeaderViewState` entries are pure separators — a header never carries a key and cannot be toggled.
- `key` in `FeatureToggleViewState` must be a real `Features` key; the VM resolves it to toggle the flag.

## Side Effects
- Writes feature flags through the `Features` provider (persisted to preferences).
- No database access, no navigation other than the back button.

## Verification
- Run: `./gradlew :feature:features:assembleDebug` (this module has no `src/test` source set).

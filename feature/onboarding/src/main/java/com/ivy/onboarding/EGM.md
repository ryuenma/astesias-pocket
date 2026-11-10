# Onboarding Package (com.ivy.onboarding)

## Overview
First-run flow: splash → import → base currency → profile name. The account,
category and Google-login steps of upstream Ivy were removed: a sideloaded private
fork has no use for Ivy's cloud sign-in, and accounts/categories arrive with an
imported backup or from `PreloadDataLogic`.

## Interface
- `enum class OnboardingState { SPLASH, IMPORT, CURRENCY, PROFILE_NAME }` — `@Immutable`, the ordered step machine.
- `OnboardingEvent` — sealed interface: `SplashNext`, `StartImport`, `ImportSkip`, `ImportFinished(success)`, `SetBaseCurrency(IvyCurrency)`, `SetProfileName(name, id)`.
- `OnboardingDetailState(currency: IvyCurrency, profileId: ProfileId, profileName: String)`.
- `BoxWithConstraintsScope.OnboardingScreen(screen: OnboardingScreen)` — entry point; renders one step per `OnboardingState`.
- Subdirectories: `./components/EGM.md`, `./steps/EGM.md`, `./viewmodel/EGM.md`.

## Invariants
- The step order is exactly `SPLASH → IMPORT → CURRENCY → PROFILE_NAME`; back always walks one step back and is consumed on `SPLASH`.
- There is no login, accounts or categories step — do not re-add them without also re-adding their data preconditions.
- The profile-name field is seeded with `""`, never a prefill: a prefilled value becomes part of what the user types.
- A failed import (`ImportFinished(success = false)`) leaves the user on the import step; advancing would drop them into an empty wallet indistinguishable from a successful import.
- Onboarding completion writes `SharedPrefs.ONBOARDING_COMPLETED = true` and resets the back stack to `MainScreen`.

## Side Effects
- Navigates (`Navigation.navigateTo(ImportScreen(launchedFromOnboarding = true))`, `navigateTo(MainScreen)`, `resetBackStack()`).
- Persists the completion flag in `SharedPrefs`.
- Registers the profile in `ProfileRegistry`.
- Seeds data (`PreloadDataLogic.preloadAccounts()` / `preloadCategories()`) only when no account exists, and schedules reminders via `TransactionReminderLogic`.

## Verification
- Run: `./gradlew :feature:onboarding:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.

# Onboarding ViewModels (com.ivy.onboarding.viewmodel)

## Overview
The onboarding state machine and its currency rules. `OnboardingRouter` owns step
transitions, completion and back handling; `OnboardingViewModel` binds it to Compose
state and performs the settings writes; `OnboardingCurrencyPolicy` holds two pure
rules kept free of Android types so they can be unit-tested off-device.

## Interface
- `OnboardingViewModel` — `@HiltViewModel`, `ComposeViewModel<OnboardingDetailState, OnboardingEvent>`; injects `Navigation`, `AccountDao`, `SettingsDao`, `WriteSettingsDao`, `ProfileRegistry`, `SharedPrefs`, `TransactionReminderLogic`, `PreloadDataLogic`. Methods: `start(screen, isSystemDarkMode)`, `importFinished(success)`, `importSkip()`.
- `OnboardingRouter(state, nav, accountDao, sharedPrefs, transactionReminderLogic, preloadDataLogic, profileRegistry)` — `initBackHandling`, `splashNext`, `startImport`, `importSkip`, `importFinished`, `currencyNext`, `setProfileNameNext(id, rawName, scope)`.
- `internal fun preselectedCurrencyCode(storedCode: String?, deviceDefaultCode: String): String` — stored currency wins unless blank.
- `internal fun shouldPersistCurrency(storedCode: String?, chosenCode: String): Boolean` — write only when there is no stored row or the user actually changed the value.

## Invariants
- **The stored currency beats the device default.** A blank/absent stored code means "nothing to adopt", so the device default is only correct for a genuinely fresh install.
- Finishing the currency step must not rewrite an unchanged preselection: that write is exactly how an imported IDR setting was previously overwritten with USD.
- Back handling is a `when` over `OnboardingState`; `SPLASH` and an unset state both consume the event (`true`).
- Only the Ivy backup import path remains; the third-party CSV importers were removed by owner decision.
- `setProfileNameNext` trims the name and falls back to `ProfileId.value` when it is blank.
- Completion order is: write `ONBOARDING_COMPLETED` → leave onboarding → seed data and schedule reminders on the caller's scope, so seeding cannot delay the transition and dies with the ViewModel.

## Side Effects
- Writes settings through `WriteSettingsDao` (base currency) and `SharedPrefs` (onboarding completed).
- Registers the profile in `ProfileRegistry`.
- Seeds accounts/categories via `PreloadDataLogic` when `accountDao.findAll()` is empty; schedules reminders via `TransactionReminderLogic.scheduleReminder()`.
- Navigation: `navigateTo(MainScreen)`, `navigateTo(ImportScreen(...))`, `resetBackStack()`, `nav.onBackPressed[screen]` registration.

## Verification
- Run: `./gradlew :feature:onboarding:testDebugUnitTest` (unit tests in `src/test`, including the currency-policy rules).

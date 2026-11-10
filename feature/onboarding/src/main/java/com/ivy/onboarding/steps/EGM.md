# Onboarding Steps (com.ivy.onboarding.steps)

## Overview
One composable per onboarding step: welcome splash, import choice, base currency and
profile name, plus the shared primary button.

## Interface
- `BoxWithConstraintsScope.OnboardingSplash(onNext: () -> Unit, …)`.
- `BoxWithConstraintsScope.OnboardingImportChoice(onImport: () -> Unit, onSkip: () -> Unit, …)`.
- `BoxWithConstraintsScope.OnboardingSetCurrency(preselectedCurrency: IvyCurrency, onSetCurrency: (IvyCurrency) -> Unit, …)`.
- `BoxWithConstraintsScope.OnboardingProfileName(initialName: String, onNext: (String) -> Unit, …)`.
- `OnboardingPrimaryButton(text: String, onClick: () -> Unit, …)` — shared CTA.
- Each step has a private `Preview`.

## Invariants
- Steps are stateless: the current value always arrives as a parameter (`preselectedCurrency`, `initialName`) and changes leave through the callback.
- `OnboardingSetCurrency` shows the value from `OnboardingCurrencyPolicy.preselectedCurrencyCode` — stored currency if any, device default only for a genuinely fresh install.
- `OnboardingProfileName` receives an empty `initialName` in practice; it must not substitute a default when blank.

## Side Effects
- None in the composables. Currency persistence and profile registration happen in `OnboardingRouter`/`OnboardingViewModel`.

## Verification
- Run: `./gradlew :feature:onboarding:testDebugUnitTest` (unit tests in `src/test`).

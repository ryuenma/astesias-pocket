# Onboarding Components (com.ivy.onboarding.components)

## Overview
Shared building blocks for the onboarding steps: the toolbar with optional back and
skip affordances, and the suggestion chips used by the text-entry steps.

## Interface
- `OnboardingToolbar(hasSkip: Boolean, onBack: () -> Unit, …)` — top bar; `hasSkip` controls whether the skip control is rendered.
- `Suggestions(suggestions: List<Any>, onAddSuggestion: (Any) -> Unit, …)` — horizontal chips plus an "add new" button; private `Suggestion`, `AddNewButton`, `class AddNew`.

## Invariants
- `Suggestions` is untyped on purpose (`List<Any>`): the callback receives the same object that was rendered, so callers cast to their own suggestion type.
- `hasSkip = false` must hide the skip control entirely rather than disable it — skipping is only legal on steps that have a fallback.
- `AddNew` is a sentinel suggestion distinguished by type, not by a string value.

## Side Effects
- None. Pure rendering plus callbacks.

## Verification
- Run: `./gradlew :feature:onboarding:testDebugUnitTest` (unit tests in `src/test`).

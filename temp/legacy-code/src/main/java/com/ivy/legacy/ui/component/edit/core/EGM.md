# Edit Transaction Bottom Sheet (core)

## Overview
The core slots of the legacy edit-transaction sheet: the sheet itself plus title, description, category, due date and toolbar. Packages are mixed here - `EditBottomSheet.kt` is in `com.ivy.wallet.ui.edit.core` while the slot files are in `com.ivy.legacy.ui.component.edit.core`; the directory, not the package, is what groups them.

## Interface
- `EditBottomSheet(...)` - the sheet; `SWIPE_UP_EXPANDED_THRESHOLD = 200` sets the drag distance that expands it.
- `ColumnScope.Title(...)` with private `Suggestions`/`Suggestion` - title field plus smart suggestions.
- `Description(...)`, `Category(...)` (+ private `CategoryButton`), `DueDate(...)` (+ private `DueDateCard`), `Toolbar(...)`.

## Invariants
- `EditBottomSheet.kt` is 956 lines and carries `@Suppress("LongMethod", "LongParameterList", "UnusedParameter", "ParameterNaming")`; this contract only covers its first 300 lines.
- `SWIPE_UP_EXPANDED_THRESHOLD` is a public constant - changing it changes the gesture feel of every edit screen.
- Each slot is a scope extension (`ColumnScope`, or plain) so the sheet controls layout order; slots must stay order-independent of each other.
- `Title`'s suggestions come from the caller (`SmartTitleSuggestionsLogic` via `domain.deprecated.logic`), not from this file.

## Side Effects
- Compose state only; every field reports changes through callbacks owned by the sheet's caller.
- No direct database or network access.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

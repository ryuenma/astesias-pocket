# Design System Utilities

## Overview
Compose/View helpers specific to the old design system: insets, keyboard handling, padding, previews, animation and density conversions. Package `com.ivy.design.utils`.

## Interface
- Insets (`Insets.kt`): `windowInsets()`, `navigationBarInsets()`, `keyboardOnlyWindowInsets()`.
- Keyboard (`Keyboard.kt`): `View.showKeyboard()`, `hideKeyboard()`, `View.hideKeyboard()`, `keyboardVisibleState()`, `View.addKeyboardListener(...)`, `isKeyboardOpen(rootView)`.
- Padding (`Padding.kt`): `Modifier.ivyPadding(IvyPadding)` plus three `padding(...)` overloads.
- Compose (`Compose.kt`): `Modifier.thenIf(...)`, `Modifier.thenWhen(...)`, `rememberInteractionSource()`, `Modifier.clickableNoIndication(...)`, `onEvent(...)`, `Int.toDensityDp()`.
- Previews (`Preview.kt`): `IvyComponentPreview(...)`, `IvyPreview(...)`, `defaultDesign(): IvyDesign`.
- View (`View.kt`): `Float.dpToPx(context)`, `Int.dpToPx(context)`. Android (`Android.kt`): `postDelayed(delayMs, run)`. Animation (`Animation.kt`).

## Invariants
- These overlap almost name-for-name with `com.ivy.legacy.utils` (`windowInsets`, `navigationBarInsets`, `keyboardOnlyWindowInsets`, `keyboardVisibleState`, `addKeyboardListener`, `isKeyboardOpen`, `rememberInteractionSource`, `clickableNoIndication`, `hideKeyboard`, `toDensityDp`) - two copies that have to be kept in sync or the two design systems behave differently.
- `defaultDesign()` returns `object : IvyWalletDesign()`, so a preview always renders against the concrete design system even if the app is running a different one.
- `toDensityDp()` is `@Composable` (it reads the density scope) while `dpToPx(context)` is not - calling one where the other is meant is a compile error, which is the only guard here.

## Side Effects
- `showKeyboard`/`hideKeyboard` mutate the IME state of the hosting view/activity.
- `addKeyboardListener` attaches a global layout listener to a root view; it must be removed or it leaks with the view.
- `postDelayed` schedules work on a view's handler.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

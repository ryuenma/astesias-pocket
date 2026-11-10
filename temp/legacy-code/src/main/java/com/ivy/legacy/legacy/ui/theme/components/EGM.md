# Legacy Theme Components

## Overview
The inherited Compose component set that the legacy screens are built from: buttons, text fields, divider primitives, the budget and buffer batteries, icon/colour pickers, currency picker, reorder modal, onboarding components and `BalanceRow`. All 36 files carry `@Deprecated("Old design system. Use `:ivy-design` and Material3")` - the fork keeps them because the remaining legacy screens have not been migrated.

## Interface
- Buttons: `IvyButton`, `IvyBorderButton`, `IvyOutlinedButton` (+ `IvyOutlinedButtonFillMaxWidth`), `IvyCircleButton`, and `CircleButtons.kt`'s `CloseButton`, `CircleButton`, `CircleButtonFilled`, `CircleButtonFilledGradient`, `BackButton`.
- Text input: `IvyBasicTextField`, `IvyOutlinedTextField`, `IvyTitleTextField` (`ColumnScope`), `IvyNameTextField`, `IvyNumberTextField`, `IvyDescriptionTextField`.
- Toggles and selections: `IvyCheckbox`, `IvyCheckboxWithText`, `IvySwitch`, `CurrencyPicker`, `IntervalPickerRow`, `ChoosePeriod`-style picker rows are not here (see `../modal`).
- Display: `BalanceRow`, `BalanceRowMedium`, `BalanceRowMini`, `BudgetBattery`, `BufferBattery`, `ProgressBar`, `ItemIconL/M/S` + `ItemIcon` internals, `IvyDividerLine`, `IvyDividerLineRounded`, `IvyDividerDot`, `IvyIcon`, `ListItem`, `DateTimeRow`, `WrapContentRow`, `ActionsRow`.
- Modals-in-components: `ChangeTransactionTypeModal`, `ReorderModal` (`private class Adapter<T : Reorderable>`), `AddPrimaryAttributeButton`, `DeleteButton`, `GradientCutBottom`, `BackBottomBar`.
- Icon plumbing: `IconInfo`, `IconStyle`, `getCustomIconId`/`getCustomIconIdS`, `fallbackToNewIconFormat` - the bridge between the old and new icon formats.
- `getTrueDate(...)` in `DateTimeRow.kt` is separately `@Deprecated("Rework this to use the TimeConverter API")`.
- Charts live one level down.

- [charts](./charts/EGM.md) - Chart components of the legacy design system.

## Invariants
- Every composable in this directory is deprecated by annotation; new call sites must use `:ivy-design`. Do not add features here.
- `ReorderModal.kt` (392L), `BudgetBattery.kt` (363L), `BufferBattery.kt` (318L), `CurrencyPicker.kt` (435L) and `ItemIcon.kt` (313L) exceed the 300-line read cap; only declarations in the first 300 lines are listed.
- `IvyIcon`, `IvyDividerDot` and `WrapContentRow` expose their previews privately - the public surface is only the composable itself.
- `ItemIcon`'s `IconStyle` enum distinguishes the icon sizes used across legacy cards; changing it changes icon resolution everywhere.

## Side Effects
- Pure rendering plus callbacks; no repository, database or network access.
- `getCustomIconId`/`getCustomIconIdS` resolve a drawable resource id from the stored icon name and fall back through `fallbackToNewIconFormat`.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

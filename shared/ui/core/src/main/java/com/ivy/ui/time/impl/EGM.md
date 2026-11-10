# com.ivy.ui.time.impl — formatting implementations

## Overview

Concrete implementations behind `TimeFormatter`, `DevicePreferences` and `DateTimePicker`. Bound in
`IvyUiBindings`; feature code should never import this package.

## Interface

- `class IvyTimeFormatter @Inject constructor(ResourceProvider, TimeProvider, TimeConverter, DevicePreferences) : TimeFormatter`
  — builds a `DateTimeFormatter` pattern per call:
  `"EEE, "` only when there is no relative day and `includeWeekDay` is true, then `"MMM d"`, then
  `" yyyy"` only when the year differs from `TimeProvider.localDateNow().year`, then
  `"HH:mm"` / `"h:mm a"` for `DateAndTime`.
- `enum class RelativeDay { Yesterday, Today, Tomorrow }`
- `class AndroidDevicePreferences @Inject constructor(@ApplicationContext Context) : DevicePreferences`
  — `DateFormat.is24HourFormat(context)`, `Locale.getDefault()`.
- `@Stable interface DateTimePicker` — `@Composable fun Content()`, `fun pickDate(initialDate: Instant?, onDatePick: (LocalDate) -> Unit)`,
  `fun pickTime(initialTime: LocalTime?, onTimePick: (LocalTime) -> Unit)`.
- `class AndroidDateTimePicker : DateTimePicker` (the Android implementation).

## Invariants

- The year is appended **only** when `dateTime.year != today.year`; a formatter that always prints
  the year changes every list row's width and breaks Paparazzi expectations.
- The relative-day prefix replaces the weekday abbreviation rather than preceding it: the pattern
  omits `"EEE, "` whenever a `RelativeDay` was resolved.
- The clock pattern is chosen by `DevicePreferences.is24HourFormat()` per call, not cached, so a
  device setting change is honoured without re-injection.
- `AndroidDevicePreferences.locale()` returns `Locale.getDefault()` on every call — unlike
  `FormatMoneyUseCase`, which caches it.
- `AndroidDateTimePicker.pickDate`/`pickTime` deliver their result through a callback; they do not
  return a value, so the caller owns the coroutine/flow that consumes it.

## Side Effects

- `AndroidDateTimePicker` shows platform dialogs (window-level UI).
- `IvyTimeFormatter` resolves strings via `ResourceProvider` (Android resources) and reads the device
  zone/clock through `TimeProvider`/`TimeConverter`.

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`

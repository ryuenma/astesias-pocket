# com.ivy.ui.time — formatting contracts

## Overview

Formatting abstractions used by every screen that shows a date or time. They exist so the
"Today/Yesterday" prefix, the year-elision rule and the locale-specific clock pattern are defined
once.

## Interface

- `interface DevicePreferences` — `fun is24HourFormat(): Boolean`, `fun locale(): Locale`.
- `interface TimeFormatter`
  - `fun LocalDateTime.format(style: Style): String`
  - `fun LocalTime.format(): String`
  - `fun Instant.formatLocal(style: Style): String`
  - `fun Instant.formatUtc(style: Style): String`
  - `sealed interface Style { val includeWeekDay: Boolean }` with
    `data class DateAndTime(includeWeekDay)` and `data class DateOnly(includeWeekDay)`.
- Subdirectories: `impl/` → [./impl/EGM.md](./impl/EGM.md)

## Subdirectories & Boundaries

- [impl/](./impl/EGM.md) — IvyTimeFormatter (relative-day prefix, year elision, 24h pattern), AndroidDevicePreferences and the DateTimePicker contract with its Android implementation.

## Invariants

- `TimeFormatter` members are **extension functions declared inside the interface**, so they are
  only callable with a formatter receiver in scope (`with(formatter) { instant.formatLocal(style) }`).
  Do not convert them to plain members — every call site would break.
- `formatLocal` converts through `TimeConverter` (device zone) while `formatUtc` uses `ZoneOffset.UTC`
  directly. The two are deliberately different; a UTC-stored transaction rendered with
  `formatLocal` and one rendered with `formatUtc` legitimately differ by the zone offset.
- `Style.includeWeekDay` controls *two* things: whether the weekday abbreviation is printed, and
  whether the relative-day prefix (Today/Yesterday/Tomorrow) is applied. There is no way to get one
  without the other.

## Side Effects

- None in the interfaces; impls read locale and the 12/24-hour device setting.

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`

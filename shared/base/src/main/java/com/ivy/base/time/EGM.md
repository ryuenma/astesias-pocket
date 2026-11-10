# com.ivy.base.time — time abstraction and safe bounds

## Overview

All clock and time-zone access funnels through this package. It exists so business logic can be
tested against a fixed clock and so that `Instant` ↔ `LocalDateTime` conversion has exactly one
definition.

## Interface

- `interface TimeProvider` — `getZoneId(): ZoneId`, `utcNow(): Instant`, `localNow(): LocalDateTime`,
  `localDateNow(): LocalDate`, `localTimeNow(): LocalTime`.
- `interface TimeConverter` — `Instant.toLocalDateTime()`, `Instant.toLocalDate()`,
  `Instant.toLocalTime()`, `LocalDateTime.toUTC()` (declared as interface member extension functions).
- `const val SafeOffsetDays = 365 * 10L`, `val INSTANT_MIN_SAFE`, `val INSTANT_MAX_SAFE`.
- `@Deprecated fun Instant.convertToLocal(): ZonedDateTime` — superseded by `TimeConverter`.

## Subdirectories & Boundaries

- [impl/](./impl/EGM.md) — TimeProvider/TimeConverter implementations: device-backed production pair plus a UTC-pinned test converter.

## Invariants

- `utcNow()` must return an `Instant`; `localNow()` must return wall-clock time in `getZoneId()`.
  Mixing the two is the single most common source of off-by-one-day bugs in this codebase.
- `INSTANT_MIN_SAFE` / `INSTANT_MAX_SAFE` are the only legal bounds for converting extreme
  instants; they reserve a 10-year (`SafeOffsetDays`) margin so `atZone` cannot overflow
  `LocalDateTime`.
- `TimeConverter` members are **extension functions declared inside an interface**, so they can
  only be called with the converter in scope (`with(converter) { instant.toLocalDate() }`) or via
  an explicit receiver. Adding a plain member function instead would change every call site.
- `Instant.convertToLocal()` is deprecated and uses `ZoneId.systemDefault()`; new code must use
  `TimeConverter`.

## Side Effects

- `INSTANT_MIN_SAFE` / `INSTANT_MAX_SAFE` are computed `get()` properties, evaluated per access —
  cheap, but not constants.

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

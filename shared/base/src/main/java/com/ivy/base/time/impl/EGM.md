# com.ivy.base.time.impl — time implementations

## Overview

Production and test implementations of the `time` abstractions. Bound in `BaseHiltBindings`:
`DeviceTimeProvider` → `TimeProvider`, `StandardTimeConverter` → `TimeConverter`.

## Interface

- `class DeviceTimeProvider @Inject constructor() : TimeProvider` — `ZoneId.systemDefault()`,
  `Instant.now()`, `LocalDateTime.now()`; the `local*` accessors derive from `localNow()`.
- `class StandardTimeConverter @Inject constructor(TimeProvider) : TimeConverter` — converts using
  the provider's zone and clamps `DateTimeException` to `INSTANT_MAX_SAFE` / `INSTANT_MIN_SAFE`
  (UTC) depending on which side of the epoch the instant falls.
- `class TestTimeConverter : TimeConverter` — fixed `ZoneId.of("UTC")`, no injection.

## Invariants

- `StandardTimeConverter.toLocalTime()` is defined as `toLocalDateTime().toLocalTime()`, not a
  separate zone conversion; keep the delegation so both stay consistent.
- The clamp direction is decided by `this > Instant.EPOCH`: instants above the epoch clamp to the
  max-safe value, below to the min-safe value. Reversing it silently maps year 10000 to year 1970.
- `TestTimeConverter` is UTC-only **by design**; tests that need a non-UTC zone must build a
  `StandardTimeConverter` with a fake `TimeProvider` instead of editing this class.
- `DeviceTimeProvider` reads `systemDefault()` on every call, so a device zone change is picked
  up mid-session. Never cache the zone in a field.

## Side Effects

- `DeviceTimeProvider` reads the device clock and zone (no mutation).
- `StandardTimeConverter` swallows `DateTimeException` and returns a clamped value instead of
  failing — callers cannot distinguish "overflowed" from "genuinely at the bound".

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

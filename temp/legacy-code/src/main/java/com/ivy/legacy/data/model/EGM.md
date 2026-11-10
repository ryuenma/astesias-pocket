# Legacy Time & UI Models

## Overview
The model types the legacy screens navigate and filter by: the time periods offered in the period selector, the from/to range, the last-N range, a calendar month, the main tab enum and the account display data.

## Interface
- `TimePeriod(...)`, `FromToTimeRange(...)`, `LastNTimeRange(...)`, `Month(...)` - time ranges (`@Immutable`; the first three carry `@Suppress("DataClassFunctions")`).
- `Month` and `TimePeriod` build their display labels (`MonthNameAbbreviationLength = 3`).
- `FromToTimeRange` conversions and filters: `toCloseTimeRange()`, `toCloseTimeRangeUnsafe()`, `toUTCCloseTimeRange()`, `todayStartOfDayUtc(...)`, and `filterUpcoming`/`filterOverdue` for both the shared and the legacy `Transaction`.
- `MainTab` - the main-screen tab enum; `AccountData(...)` - account display data.

## Invariants
- `toCloseTimeRange()` can fail and `toCloseTimeRangeUnsafe()` cannot - the "unsafe" variant is the one that returns a range for an invalid input, so a value it produced must not be assumed valid.
- `filterUpcoming`/`filterOverdue` exist twice (shared and legacy `Transaction`); the legacy ones are `@Deprecated("Uses legacy Transaction")`.
- Ranges are UTC-based (`toUTCCloseTimeRange`, `todayStartOfDayUtc`), so a caller mixing local and UTC boundaries shifts the period by the UTC offset.
- `MonthNameAbbreviationLength = 3` fixes the abbreviation width used across the UI; a longer month name will not be truncated.

## Side Effects
- Reads the clock in `todayStartOfDayUtc(...)` - it is the one not-pure function here.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

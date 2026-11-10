# Global Settings Actions

## Overview
The start-day-of-month setting, which decides where the monthly boundary falls for budgets and reports.

## Interface
- `StartDayOfMonthAct` - reads the configured start day.
- `UpdateStartDayOfMonthAct` - writes it.

## Invariants
- The value is read by budget and report aggregation; changing it re-frames existing periods without rewriting rows, so historical reports can shift after a change.
- Split into a read action and a write action on purpose - screens call the read one freely.

## Side Effects
- `UpdateStartDayOfMonthAct` persists the setting.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

# Settings Actions

## Overview
Read and write the app settings through actions, plus the derived base currency and buffer difference. Note the mixed packages: `CalcBufferDiffAct` is in `com.ivy.legacy.domain.action.settings` while the other three are in `com.ivy.wallet.domain.action.settings`.

## Interface
- `SettingsAct(...)` - reads the current `Settings`.
- `UpdateSettingsAct(...)` - writes settings.
- `BaseCurrencyAct(...)` - reads the base currency code.
- `CalcBufferDiffAct() : FPAction<CalcBufferDiffAct.Input, BigDecimal>` - the buffer difference (buffer amount minus the balance it protects).

## Invariants
- `Settings` (`com.ivy.legacy.datamodel.Settings`) stores `bufferAmount` as `BigDecimal` while `SettingsEntity` stores it as `Double`; `toEntity()` performs that narrowing, so a buffer amount with more precision than a `Double` survives the wrong way round.
- `UpdateSettingsAct` is the only writer here - reading actions must not be given write side effects, because several screens call them on every recomposition.
- `CalcBufferDiffAct` is a plain `FPAction` with a parameterless constructor: it has no dependency, so it can be unit-tested without Hilt.

## Side Effects
- `UpdateSettingsAct` writes settings to persistent storage (Room/DataStore through the injected writer).
- The read actions only read.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

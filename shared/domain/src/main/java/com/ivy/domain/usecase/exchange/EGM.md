# com.ivy.domain.usecase.exchange — currency exchange

## Overview

Conversion between currencies and the background refresh of the rate table.

## Interface

- `class ExchangeUseCase @Inject constructor(ExchangeRatesRepository)`
  - `suspend fun convert(value: PositiveValue, to: AssetCode): Option<PositiveValue>`
  - `suspend fun convert(values: Map<AssetCode, NonZeroDouble>, to: AssetCode): ExchangeResult`
  - `suspend fun convert(value: Value, to: AssetCode): Option<Value>`
- `data class ExchangeResult(val exchanged: Option<Value>, val exchangeErrors: Set<AssetCode>)`.
- `class SyncExchangeRatesUseCase @Inject constructor(...)` — `suspend fun sync(baseCurrency: AssetCode): Either<String, Unit>`.

## Invariants

- **All three `ExchangeUseCase.convert` overloads are `TODO("Not implemented")`.** They compile and are
  injectable, but every call throws `NotImplementedError` at runtime. This is the single most important
  fact about this package: nothing in this module may start depending on `ExchangeUseCase` without
  implementing it first, and the `@Suppress("UnusedPrivateProperty", "UnusedParameter")` on the class is
  a consequence of the repository being injected but unused.
- `convert(map, to)` signals partial failure through `ExchangeResult.exchangeErrors` (a set of assets
  that could not be converted) while still returning the successful ones — so a caller must inspect the
  set, not only the `Option`.
- An empty input map must yield `Option.None` with an empty error set (per the KDoc), which is different
  from "every conversion failed".
- `SyncExchangeRatesUseCase.sync` returns `Either.Left` on network failure and is the only implemented
  rate path; it is covered by an `androidTest`.

## Side Effects

- `SyncExchangeRatesUseCase.sync` performs a network fetch and writes exchange-rate rows.
- `ExchangeUseCase.convert` would read the rate table (when implemented).

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`
- Sync: `./gradlew :shared:domain:connectedDebugAndroidTest`

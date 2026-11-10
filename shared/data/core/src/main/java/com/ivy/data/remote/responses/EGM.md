# com.ivy.data.remote.responses — wire DTOs

## Overview

Dormant transfer objects for HTTP payloads. Only the exchange-rate response today.

## Interface

- `@Serializable @Keep data class ExchangeRatesResponse(val date: String, @SerialName("eur") val rates: Map<String, Double>)`

## Invariants

- `@SerialName("eur")` maps the JSON key `eur` to `rates`. The upstream document is
  `{"date": "...", "eur": {"usd": 1.08, ...}}`, so `rates` is keyed by **lowercase** currency code —
  a consumer must uppercase before building an `AssetCode` (which normalises to uppercase anyway).
- `@Keep` prevents R8 from stripping the serializable members; removing it produces empty maps at
  runtime rather than a compile error.
- `rate` values are raw `Double`, unvalidated. They become `PositiveDouble` only in the mapper, so a
  zero or negative published rate is caught there, not here.

## Side Effects

- None (deserialisation only).

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

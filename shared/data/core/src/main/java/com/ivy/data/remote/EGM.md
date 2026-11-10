# com.ivy.data.remote — remote data sources

## Overview

Contracts for data fetched over the network. Only exchange rates are remote today; sync lives in
`com.ivy.data.sync` with its own HTTP layer.

## Interface

- `interface RemoteExchangeRatesDataSource`
  - `suspend fun fetchEurExchangeRates(): Either<String, ExchangeRatesResponse>`

## Subdirectories & Boundaries

- [impl/](./impl/EGM.md) — Ktor-based EUR exchange-rate fetcher with four fallback CDN URLs serialising the first successful response into Either.
- [responses/](./responses/EGM.md) — Wire DTO for the exchange-rate API: date plus an eur-keyed map of lowercase currency codes to raw Double rates.

## Invariants

- The contract is **EUR-based**: `fetchEurExchangeRates` returns the provider's `eur` table, and any
  other base currency is derived by the caller. Requesting a different base here would break
  `ExchangeRatesRepository`'s cross-rate maths.
- Failures are returned as `Either.Left(String)`, never thrown — a network failure must not crash a
  background rate refresh.

## Side Effects

- Implementations perform HTTP requests.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

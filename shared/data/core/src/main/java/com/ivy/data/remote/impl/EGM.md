# com.ivy.data.remote.impl — exchange-rate HTTP client

## Overview

Ktor implementation of the exchange-rate source. Targets the free `fawazahmed0/currency-api`
distribution with four fallback URLs, because the free CDN endpoints go down independently.

## Interface

- `class RemoteExchangeRatesDataSourceImpl @Inject constructor(private val ktorClient: dagger.Lazy<HttpClient>) : RemoteExchangeRatesDataSource`
  - `suspend fun fetchEurExchangeRates(): Either<String, ExchangeRatesResponse>`
  - private `fetchEurExchangeRates(url: String)` wrapped in `Raise.catch`.

## Invariants

- `HttpClient` is injected as **`dagger.Lazy`**, not directly: the client is built with the
  logging plugin and a serializer, and injecting it eagerly would construct it — and its
  engine — during graph creation even when no rate refresh ever runs.
- The four URLs are tried in order and the **first success wins**; the last failure is what is
  returned if all four fail. Nothing distinguishes "all endpoints down" from "the fourth one
  failed", so callers should not surface the message as authoritative.
- An empty initial `Either.Left("Impossible")` is the loop's seed; it can only be observed if the
  URL list is emptied, which would be a bug.

## Side Effects

- Up to four sequential HTTPS GETs to `currency-api.pages.dev` and `cdn.jsdelivr.net`.
- Request bodies and responses are logged at `LogLevel.BODY` through `Timber` (see
  `KtorClientModule`).

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

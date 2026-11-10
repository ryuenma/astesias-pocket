# Pure Currency Exchange

## Overview
Currency conversion expressed as pure functions over an explicit rate: build an `ExchangeData`, validate it, then convert. `ExchangeTrns` layers transaction-aware conversion on top, including the base-currency roll-up.

## Interface
- `ExchangeData(...)` - the conversion request (amount, from/to currency, rate).
- `suspend fun exchange(...)`, `suspend fun validExchangeRate(...)`, `ExchangeRate.validateRate(): Option<BigDecimal>`, `String.validateCurrency(): Option<String>` - all `@Pure`.
- `exchangeInBaseCurrency(...)`, `exchangeInCurrency(...)` - transaction conversions, each also with a legacy-`Transaction` overload.
- `ExchangeEffect` typealias - `suspend (ExchangeData, BigDecimal) -> Option<BigDecimal>`, the seam that lets a caller supply the conversion.
- `ExchangeTrnArgument(...)` and the `LegacyExchangeTrns` object for the legacy transaction type.

## Invariants
- Validation returns `Option`, not exceptions: an invalid currency or missing rate yields `None` and the caller must decide, never a silent 1:1 conversion.
- `ExchangeTrns` takes the conversion as a parameter (`ExchangeEffect`) instead of resolving rates itself, which is what keeps it pure and testable.
- The legacy-`Transaction` overloads are marked `@Deprecated("Uses legacy Transaction")`; they are kept for the legacy screens only.
- Same-direction convention as `deprecated/logic/currency`: the rate converts from the source currency into the target one.

## Side Effects
- None: the rate is passed in, not fetched.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

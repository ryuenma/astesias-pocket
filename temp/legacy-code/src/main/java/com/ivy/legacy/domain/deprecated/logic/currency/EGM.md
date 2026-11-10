# Exchange Rates Logic

## Overview
The pre-FP-style currency conversion helpers. `ExchangeRatesLogic` itself takes injected repositories, while the extension functions are `suspend` because they resolve rates.

## Interface
- `ExchangeRatesLogic(...)` - rate lookup and conversion entry point.
- `Iterable<Transaction>.sumInBaseCurrency(...)` - sums a transaction list in the base currency.
- `Iterable<PlannedPaymentRule>.sumByDoublePlannedInBaseCurrency(...)` - same for planned payment rules.

## Invariants
- Direction matters: base = BGN, currency = EUR produces the rate 0.51, i.e. the returned factor converts base into the requested currency, not the other way round.
- The whole file is deprecated in favour of the FP style in `../../fp`; new code should use `domain/pure/exchange`.

## Side Effects
- Resolves exchange rates through the injected repository (database reads, possibly network refresh elsewhere).

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

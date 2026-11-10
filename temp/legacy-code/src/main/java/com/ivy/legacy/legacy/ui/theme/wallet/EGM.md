# Legacy Wallet Amount UI

## Overview
Wallet-screen presentation of an amount and its currency, plus the period selector. The `AmountCurrency*` family is the typography contract for money in the legacy UI: each variant is a different size/weight row (B1/B2/H1/H2/caption) around the same formatted amount.

## Interface
- `AmountCurrencyB2Row`, `AmountCurrencyB1Row`, `AmountCurrencyB1`, `AmountCurrencyH1`, `AmountCurrencyH2Row`, `AmountCurrencyCaption` - the size variants.
- `PeriodSelector(...)` - the month/period switcher used by wallet and reports.

## Invariants
- Amounts are passed in pre-formatted (`String`) together with the currency; formatting itself belongs to `com.ivy.legacy.utils` (`Double.format(currency)`), so these composables never call `DecimalFormat`.
- `AmountCurrencyB2Row` and `PeriodSelector` are deprecated; the non-row variants (`AmountCurrencyB1/B1/H1/H2Row/Caption`) are the ones still annotated as current.
- The amount `String` is the source of truth for width - these rows size to their content and are meant to be nested in a `Column`, not stretched.

## Side Effects
- Pure rendering; no state, no I/O.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

# Legacy Domain Data Types

## Overview
Types shared across the legacy UI and domain: the currency catalogue, sort orders, the reorderable marker interface and the exchange-rate/date-divider view states.

## Interface
- `IvyCurrency` - the currency catalogue entry: code, name, symbol, decimal digits, and `isCrypto`; `IvyCurrency.fromCode(code)` is the lookup used everywhere, including by amount formatting.
- `SortOrder(orderNum: Int, displayName: String)` - the enum that orders lists.
- `Reorderable` - marker interface for lists the user can reorder.
- `CustomExchangeRateState`, `TransactionHistoryDateDivider` - `@Immutable` view state.

## Invariants
- `IvyCurrency.fromCode` returns null for an unknown code; every caller must handle null rather than assume a default, which is why `Double.format(currency: IvyCurrency?)` takes a nullable.
- `IvyCurrency.isCrypto` is the switch that picks 9-decimal crypto formatting over 2-decimal fiat formatting in `com.ivy.legacy.utils`; it is a formatting contract, not a display preference.
- `SortOrder.orderNum` is persisted - changing an enum's number reorders existing saved views.
- `Reorderable` is a marker only: it grants no behaviour, it just lets the reorder modal accept a list.

## Side Effects
- None; these are value types.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

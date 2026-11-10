# Pure Account Functions

## Overview
Two account-level helpers used when aggregating wallet figures.

## Interface
- `filterExcluded(accounts: List<Account>): List<Account>` - drops accounts excluded from balance/wallet totals.
- `accountCurrency(account: Account, baseCurrency: String): String` - the currency an account is expressed in, falling back to the base currency.

## Invariants
- `filterExcluded` is the single filter point: any total that must honour the "excluded" flag has to call it, and a total that skips it will disagree with the wallet balance.
- `accountCurrency` falls back to the base currency rather than to a hardcoded one, so changing the base currency re-labels every such account.
- Pure functions over the shared `Account` model, with no repository access.

## Side Effects
- None.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

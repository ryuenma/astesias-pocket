# com.ivy.domain.usecase.wallet — whole-ledger totals

## Overview

Aggregates across all accounts: the total balance per currency and the wallet-wide statistics.

## Interface

- `class WalletBalanceUseCase @Inject constructor(...)`
  - `suspend fun calculate(outCurrency: AssetCode): ExchangedWalletBalance`
  - `suspend fun calculate(): Map<AssetCode, NonZeroDouble>`
  - `data class ExchangedWalletBalance(...)`
- `class WalletStatsUseCase @Inject constructor(...)` — `suspend fun calculate(...)` (raw) and
  `suspend fun calculate(...)` (exchanged); `data class WalletStats(...)`, `data class ExchangedWalletStats(...)`.

## Invariants

- The **no-argument `calculate()` never converts**: it returns one entry per currency actually held.
  Anything that renders a single "Total Balance" must use the `outCurrency` overload, which can fail to
  convert currencies with no rate.
- Total Balance is computed from accounts whose `includeInBalance` is true. Total Savings is the
  *difference* between the all-accounts total and that figure — there is no savings-specific arithmetic,
  so adding one double-counts.
- `WalletBalanceUseCase` delegates accumulation to `BalanceBuilder`, so an account whose computed balance
  turns negative is dropped rather than shown as a negative total.
- Suppressions acknowledge unused injected repositories across overloads.

## Side Effects

- Reads accounts, transactions and exchange rates through repositories.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

# com.ivy.domain.usecase.account — account maths

## Overview

Balance and statistics calculations for a single account, in both the raw multi-currency form and an
exchanged single-currency form.

## Interface

- `class AccountBalanceUseCase @Inject constructor(...)`
  - `suspend fun calculate(...)` (raw: `Map<AssetCode, NonZeroDouble>` shape)
  - `suspend fun calculate(...)` (exchanged: `ExchangedAccountBalance`)
  - `data class ExchangedAccountBalance(...)`
- `class AccountStatsUseCase @Inject constructor(...)` — four `suspend fun calculate(...)` overloads
  returning `AccountStats` / `ExchangedAccountStats`, plus `data class AccountStats(...)` and
  `data class ExchangedAccountStats(...)`.

## Invariants

- Both classes carry `@Suppress("UnusedPrivateProperty", "UnusedParameter")`: injected repositories are
  not all used by every overload. Removing the suppression is a signal that an overload really is dead.
- The raw and exchanged forms are **separate entry points**, not a flag: the raw form never invents an
  exchange rate, so an account in an unsupported currency is representable instead of being zeroed.
- `AccountBalanceUseCase` delegates the accumulation to `com.ivy.domain.usecase.BalanceBuilder`, so the
  "negative balance is dropped" rule applies here too.
- Statistics are per-account and per-currency; aggregating across accounts is `WalletStatsUseCase`'s job.

## Side Effects

- Reads transactions, accounts and exchange rates through repositories (`dispatchers.io`).
- The exchanged overloads may trigger an exchange-rate lookup per currency.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

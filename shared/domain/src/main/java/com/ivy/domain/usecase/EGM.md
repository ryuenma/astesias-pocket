# com.ivy.domain.usecase — business operations

## Overview

The app's use cases, grouped by subject, plus two mutable builders that the statistics use cases drive.
Everything here reads repositories from `data/core` and exposes a single named operation.

## Interface

- `class BalanceBuilder` — `processDeposits(incomes, transfersIn)`,
  `processWithdrawals(expenses, transfersOut)`, `build(): Map<AssetCode, NonZeroDouble>`.
- `class StatSummaryBuilder` — `process(value: PositiveValue)`, `build(): StatSummary`.

## Subdirectories & Boundaries

- [account/](./account/EGM.md) — Per-account balance and statistics use cases, each offering a raw multi-currency form and an exchanged single-currency form.
- [android/](./android/EGM.md) — DeviceIdUseCase: a random UUID persisted in DataStore to identify the install without permissions, exposed as the DeviceId value class.
- [billreminder/](./billreminder/EGM.md) — Monthly bill-reminder calendar arithmetic (BillReminderDates) and the pending-bill inbox that materialises occurrences with a stable occurrence key.
- [category/](./category/EGM.md) — Per-category statistics use case with raw and exchanged overloads; parent totals exclude subcategories unless the caller supplies rolled-up ids.
- [csv/](./csv/EGM.md) — CSV export (file, rows, string) and a raw row-splitting reader, with IvyCsvRow as the exported row shape.
- [exchange/](./exchange/EGM.md) — Currency exchange package: ExchangeUseCase (all three convert overloads are TODO/NotImplementedError) and the implemented SyncExchangeRatesUseCase rate refresh.
- [transfer/](./transfer/EGM.md) — Cross-profile transfer pipeline: leg composition (CrossProfileTransfer), publishing, pure inbox decisions over PendingTransferState, pending/confirmation repositories and the one-shot sync runner.
- [wallet/](./wallet/EGM.md) — Wallet-wide totals: per-currency balances and exchanged single-currency totals, plus wallet statistics; Total Savings is derived from includeInBalance, not computed separately.
- [xlsx/](./xlsx/EGM.md) — XLSX export: ExportXlsxUseCase plus the dependency-free XlsxWriter that assembles OOXML ZIP parts with inline strings.

## Invariants

- `BalanceBuilder.build()` returns the **internal mutable map**, not a copy. A caller that mutates the
  returned `Map` (or keeps it across a later `process*` call) sees the builder's state change under it.
  Treat the result as a snapshot to be copied before handing it to UI.
- `processWithdrawals` only *records* a new balance when the subtraction stays `NonZeroDouble`-valid.
  A withdrawal that would make the balance **negative is silently dropped** (`sub != 0.0` → neither
  branch runs), and one that lands exactly on `0.0` removes the asset key. Overspending is therefore
  not represented, not clamped.
- `processDeposits`/`processWithdrawals` accumulate across calls; a builder is single-use per
  computation and must not be reused for a second account.
- `combine` sums two maps and keeps the `a`-side value when the sum overflows `PositiveDouble` — an
  overflow is dropped silently, never reported.
- `StatSummaryBuilder.build()` uses `NonNegativeInt.unsafe(count)`, justified because `count` is a
  non-negative counter incremented by one.
- Both builders are plain classes with no Hilt scope: the callers construct one per calculation.

## Side Effects

- None: pure in-memory accumulation.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

# Domain Actions

## Overview
The action layer of the legacy stack: `@Inject`-constructed units that read repositories (and exchange rates) and return either an entity or a derived value. They are the FP-style replacement for the creators and logic classes in `../deprecated/logic`, and they are the layer ViewModels bind to.

## Subdirectories & Boundaries
- [account](./account/EGM.md) - Account actions: list/by-id lookups, account transactions, balance and income/expense calculations.
- [budget](./budget/EGM.md) - `BudgetsAct`, the single action reading all budgets.
- [category](./category/EGM.md) - Category transaction filters, including the deprecated legacy-`Transaction` variant.
- [exchange](./exchange/EGM.md) - `ExchangeAct`, currency conversion between two currencies.
- [global](./global/EGM.md) - Global app settings actions: read and update the start day of month.
- [loan](./loan/EGM.md) - Loan list and loan-by-id actions.
- [settings](./settings/EGM.md) - Settings actions: read/update settings, base currency and the buffer difference.
- [transaction](./transaction/EGM.md) - Transaction queries and aggregates: history, due/upcoming, date dividers and income/expense totals.
- [viewmodel](./viewmodel/EGM.md) - ViewModel-facing actions that assemble screen state for the legacy UI.
- [wallet](./wallet/EGM.md) - Wallet-wide balance and income/expense actions across all accounts.

## Invariants
- Actions come in two shapes: `Action<I, O>` and `FPAction<I, O>` from `com.ivy.frp.action`; a caller always goes through `invoke`, never through constructor state.
- Read actions live here; anything that writes lives in `../deprecated/logic` (creators) or a feature-level use case - this package is read/derive only.
- Where a caller still holds the legacy `com.ivy.base.legacy.Transaction`, an `Legacy*`-prefixed twin exists and is annotated `@Deprecated("Uses legacy Transaction")`. Both must stay behaviourally identical; the twin is not an alternative implementation.
- Actions are grouped by entity: `account`, `budget`, `category`, `exchange`, `global`, `loan`, `settings`, `transaction`, `wallet`, plus `viewmodel` for screen-shaped state.

## Side Effects
- Repository and exchange-rate reads; no writes.

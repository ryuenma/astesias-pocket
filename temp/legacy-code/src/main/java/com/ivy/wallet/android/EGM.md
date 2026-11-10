# Legacy `com.ivy.wallet.android` Chain

## Overview
Package chain for the one `com.ivy.wallet.android` package inside `:temp:legacy-code` (`notification`). The other `com.ivy.wallet.*` packages in this module live under `legacy/domain` and `legacy/ui`, whose directories say `legacy` but whose packages say `wallet`.

## Subdirectories & Boundaries
- [notification](./notification/EGM.md) - `IvyNotificationId`, the allocator that hands out notification ids.

## Invariants
- Path and package disagree throughout this module: this chain is the only place where the directory happens to match its package.
- Its sibling `com.ivy.legacy.android.notification` (under `../legacy/android/notification`) is a different package with a similar name - check imports, not paths.

## Side Effects
- None: grouping level.

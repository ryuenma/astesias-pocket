# Legacy `com.ivy.wallet` Root

## Overview
Package root `com.ivy.wallet` inside `:temp:legacy-code`. It holds only the Android notification package, which is the one part of the module that kept a matching directory.

## Subdirectories & Boundaries
- [android](./android/EGM.md) - Package chain `com.ivy.wallet.android` inside `:temp:legacy-code`.

## Invariants
- Most code that declares `com.ivy.wallet.*` packages is **not** here - it lives under `../../legacy/domain`, `../../legacy/legacy/ui` and `../../legacy/utils`, so a search by directory and a search by package give different answers.
- Adding a new `com.ivy.wallet.*` file: put it where its siblings are, not here, unless it belongs to the notification id allocator.

## Side Effects
- None: grouping level.

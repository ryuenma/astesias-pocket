# Legacy Test `com.ivy` Root

## Overview
Top-level test package of `:temp:legacy-code`. Only `legacy` is present, and it mirrors the `com.ivy.legacy` production package so a test sits at the same package path as its subject (which is what gives it access to `internal` declarations).

## Subdirectories & Boundaries
- [legacy](./legacy/EGM.md) - Test root `com.ivy.legacy` of `:temp:legacy-code`; contains the logout logic test.

## Invariants
- Keeping the test package equal to the production package is load-bearing: `internal` members are only visible that way.
- There is no `com/ivy/wallet` test tree here even though `com.ivy.wallet.*` production packages exist in this module.

## Side Effects
- None.

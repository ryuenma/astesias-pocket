# ViewModel Actions

## Overview
Actions that exist purely to assemble screen state for legacy ViewModels, as opposed to the entity-level actions in the sibling packages.

## Subdirectories & Boundaries
- [account](./account/EGM.md) - `AccountDataAct`, assembling account display data for the account screen.
- [home](./home/EGM.md) - Home-screen actions: due/overdue/upcoming info, balance and income visibility, cache refreshes.

## Invariants
- No production code lives directly in this directory - it is a grouping level, so its contract is the set of sub-directories listed above.
- These actions exist so a legacy ViewModel does not assemble screen state inline; they are read-only and must stay that way - a mutation here would be a write hiding behind a ViewModel-shaped name.

## Side Effects
- None of its own: this directory contributes no runtime behaviour, only the contract of the packages below it.

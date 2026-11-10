# Legacy Charts

## Overview
Chart components of the legacy design system. Today it holds a single sub-package, `linechart`, which is the only chart the legacy screens still draw; the Composition `Canvas` drawing lives entirely there, so this level carries no code of its own.

## Subdirectories & Boundaries
- [linechart](./linechart/EGM.md) - `IvyLineChart`, the hand-rolled Canvas line chart with tap handling.

## Invariants
- No production code lives directly in this directory - it is a grouping level, so its contract is the set of sub-directories listed above.
- Only `linechart` exists: adding a second chart package means adding a child here, and the parent contract must then name it, or the tree below this level becomes undiscoverable.

## Side Effects
- None of its own: this directory contributes no runtime behaviour, only the contract of the packages below it.

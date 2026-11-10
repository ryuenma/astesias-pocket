# Legacy `com.ivy.wallet.ui.theme` Tree

## Overview
Package chain for the Compose UI that was written against the old design system under `com.ivy.wallet.ui.theme`. It contains no source of its own; the theme, components and modal trees hang below it, and their packages still say `com.ivy.wallet.ui.theme.*` even though the directories now live under `com/ivy/legacy/...`.

## Subdirectories & Boundaries
- [ui](./ui/EGM.md) - Legacy Compose UI package root; holds the colour picker and the theme tree.

## Invariants
- The directory name is a historical accident: `legacy/legacy/` carries the old package name, not a second legacy layer.
- Files here mix package roots - `com.ivy.wallet.ui.theme.*`, `com.ivy.legacy.legacy.ui.theme.*` and `com.ivy.domain.legacy.ui.*` all appear in this subtree, so imports cannot be derived from paths.

## Side Effects
- None: grouping level.

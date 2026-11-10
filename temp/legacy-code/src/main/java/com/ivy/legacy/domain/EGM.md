# Legacy Domain Layer

## Overview
The domain layer of the inherited stack, split by how much ceremony a calculation needs: `action` (injected, reads repositories), `pure` (annotated `@Pure`, no I/O), `deprecated/logic` (the writers), and `data` (shared value types).

## Subdirectories & Boundaries
- [action](./action/EGM.md) - Injected `Action`/`FPAction` units that read repositories and compute derived values for the UI.
- [data](./data/EGM.md) - Domain data types shared by the legacy UI: `IvyCurrency`, `SortOrder`, `Reorderable`, exchange-rate state and date-divider types.
- [deprecated](./deprecated/EGM.md) - Superseded domain code kept for callers that have not migrated yet.
- [pure](./pure/EGM.md) - Pure functions annotated `@Pure`/`@Total`: no I/O, no clock, no repositories.

## Invariants
- Reads go through `action`, writes through `deprecated/logic` - a screen that reads through the creators is reading through the write path.
- `pure` must stay free of clocks, repositories and loggers; time is passed in (`TimeProvider`/`TimeConverter`) rather than read.
- Both the shared `com.ivy.data.model.Transaction` and the legacy `com.ivy.base.legacy.Transaction` are handled here, the second always behind a `Legacy*` twin annotated `@Deprecated("Uses legacy Transaction")`.
- Several packages are `com.ivy.wallet.domain.*` on disk under `com/ivy/legacy/domain` - directory and package disagree.

## Side Effects
- Repository and exchange-rate reads (`action`), database writes (`deprecated/logic`).
- No network access in this tree; sync lives in `../sync`.

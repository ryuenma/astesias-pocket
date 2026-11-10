# com.ivy — organization package root (domain)

## Overview

`com.ivy` package root of `:shared:domain`; contains only `domain/`.

## Interface

- `domain/` → [./domain/EGM.md](./domain/EGM.md)

## Subdirectories & Boundaries

- [domain/](./domain/EGM.md) — Domain root package: the AppStarter and RootScreen ports the app implements (activity launch, build metadata, sharing, browser) plus the di/features/model/usecase subpackages.

## Invariants

- No files at this level. Note that `com.ivy.domain.db` (Room converters and legacy migrations) lives
  in the `shared/data/core` module, not here — the package prefix does not follow the module boundary.

## Side Effects

- None.

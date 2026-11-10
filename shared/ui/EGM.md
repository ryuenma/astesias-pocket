# shared/ui — presentation modules

## Overview

Groups the reusable presentation layer: `core` (Material 3 design system, shared composables,
formatters, time formatting) and `navigation` (the screen graph and its `Navigation` back stack).
Both are Android library modules that depend on `shared/domain` for use cases and on
`shared/base` for primitives; neither depends on `app/` or any `feature/` module, so they stay
reusable across screens.

## Interface

- `core/` → [./core/EGM.md](./core/EGM.md) — design system, components, `FormatMoneyUseCase`, time formatting.
- `navigation/` → [./navigation/EGM.md](./navigation/EGM.md) — `Navigation`, `Screen`, `Screens`, `NavigationRoot`.
- `testing/` — an additional Gradle module in this directory (`build.gradle.kts` present) that is
  outside this documentation partition; no contract file is emitted for it here.

## Subdirectories & Boundaries

- [core/](./core/EGM.md) — Shared UI module (com.ivy.ui): Material 3 theme, colors, shared components, money/time formatters.
- [navigation/](./navigation/EGM.md) — Screen graph module (com.ivy.navigation): Screen/Screens sealed model, Singleton Navigation back stack, NavigationRoot rendering and screen-scoped ViewModels.

## Invariants

- Dependency direction is one-way: `ui/navigation → ui/core → domain → data/core`. `ui/core` must
  not know about `navigation` (that is why `navigation` depends on `core` and not the reverse).
- No `ui` module may depend on a `feature/*` or `app` module: shared UI must stay feature-agnostic.

## Side Effects

- None at this level; Compose side effects live in `core/` and `navigation/`.

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`

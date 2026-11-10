# `temp/old-design` Module

## Overview
Gradle module `:temp:old-design`, namespace `com.ivy.design`. The inherited design system: level 0 tokens, level 1 primitives, level 2 components, the `IvyDesign` API and its concrete implementation. `:temp:legacy-code` depends on it, so the remaining legacy screens still render through it.

## Interface
- Build script (`build.gradle.kts`): plugin `ivy.feature`; `android.namespace = "com.ivy.design"`.
- Project dependencies: `shared.base`, `shared.ui.core`, `shared.domain`.
- Layering: `l0_system` (tokens) → `l1_buildingBlocks` (primitives) → `l2_components` (components), with `api`/`api/systems` binding the tokens and `utils` holding the shared Compose/View helpers.
- No test source set and no `res` of its own.

- [src](./src/EGM.md) - Source root of `:temp:old-design`; only a `main` source set exists (no tests).

## Invariants
- The whole module is deprecated in favour of `:ivy-design` + Material3, but it cannot be removed while `:temp:legacy-code` imports `com.ivy.design.*`.
- Dependency direction is one-way and load-bearing: `l0` must not import `l1`/`l2`, and `l1` must not import `l2`. Reversing it makes the token layer un-replaceable.
- The module pulls only `shared.base`, `shared.ui.core` and `shared.domain` - it deliberately knows nothing about data, navigation or features, which is what lets it be swapped wholesale.
- `com.ivy.wallet.ui.theme.IvyColors` in `:temp:legacy-code` is a **second** copy of this module's palette; a colour change has to be made in both or the two UI generations diverge.

## Side Effects
- None at runtime: the module is tokens and composables, with no storage, network or background work.

## Verification
- Run: `./gradlew :temp:old-design:compileDebugKotlin`

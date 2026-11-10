# Legacy Module Packages

## Overview
Top-level package split of `:temp:legacy-code`. Two roots coexist: `legacy` (almost everything) and `wallet` (the notification id allocator). Both are inside a module whose Gradle namespace is `com.ivy.legacy`.

## Subdirectories & Boundaries
- [legacy](./legacy/EGM.md) - Package root `com.ivy.legacy`: app context, constants, Compose root helpers, logout logic and loan formatting extensions.
- [wallet](./wallet/EGM.md) - Package root `com.ivy.wallet` inside this module; only the Android notification-id allocator lives here.

## Invariants
- Do not infer a file's package from its directory: `com.ivy.wallet.domain.action.*`, `com.ivy.legacy.domain.action.*`, `com.ivy.frp.*` and `com.ivy.domain.legacy.ui.*` all appear inside the `legacy` tree.
- The Gradle namespace (`com.ivy.legacy`) governs the generated `R`/`BuildConfig`, not the source packages - adding a package that collides with another module's namespace is what breaks the build, not an unusual package name.

## Side Effects
- None: grouping level.

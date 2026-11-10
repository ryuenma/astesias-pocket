# shared — shared library modules

## Overview

The feature-agnostic library layer consumed by `app/` and by the `feature/` modules. Four groups:

- `base/` — platform primitives and the deprecated legacy quarantine. Depends on nothing.
- `data/` — `model` (pure domain types), `core` (Room/Ktor implementation), `model-testing` (fixtures).
- `domain/` — use cases, feature flags, and the `AppStarter`/`RootScreen` ports the app implements.
- `ui/` — `core` (design system, formatters) and `navigation` (screen graph).

## Interface

- `base/` → [./base/EGM.md](./base/EGM.md) — dispatchers, time, resources, serialization, enums, legacy.
- `data/` → [./data/EGM.md](./data/EGM.md) — the data layer.
- `domain/` → [./domain/EGM.md](./domain/EGM.md) — use cases and feature flags.
- `ui/` → [./ui/EGM.md](./ui/EGM.md) — presentation.

## Subdirectories & Boundaries

- [base/](./base/EGM.md) — Lowest-level shared Android library (com.ivy.base): dispatcher/time/resource abstractions, Hilt modules, serialization adapters, enums and the deprecated legacy quarantine.
- [data/](./data/EGM.md) — Data-layer grouping directory: immutable domain model, Room/Ktor core implementation, and shared test fixtures.
- [domain/](./domain/EGM.md) — Business-logic module (com.ivy.domain): use cases for accounts, categories, wallets, transfers, bill reminders, CSV/XLSX export and exchange rates, plus feature flags and the AppStarter/RootScreen ports.
- [ui/](./ui/EGM.md) — Presentation-layer grouping: design system/components/formatters (core) and the screen graph (navigation).

## Invariants

- **The dependency direction is enforced by Gradle configuration, not convention.**
  `base` declares no project deps; `data/model` declares none; `data/core` declares
  `api(base)`, `api(data/model)`; `domain` declares `implementation(base)`,
  `implementation(data/core)`; `ui/core` declares `implementation(base)`, `implementation(domain)`;
  `ui/navigation` adds `implementation(ui/core)`. Nothing declares `ui`, `domain` or a `feature/*`
  module at a higher position than itself.
- **`shared/data/core` must never depend on `shared/domain`**, and `shared/base` must never depend on
  any sibling. Either edge is a cycle, because `domain` already depends on both.
- **Room is `implementation` in `data/core`, not `api`.** Consumers must never see Room types in an
  injected signature, or Hilt's KSP step fails with `error.NonExistentClass`.
- `base` and `model` are `api` dependencies of `data/core` on purpose: `ProfileRegistry` and
  `SharedPrefs`-bearing constructors are part of that module's public API and consumers receive them
  through Hilt.
- No module under `shared/` may depend on `app/` or `feature/*`; the ports in `domain`
  (`AppStarter`, `RootScreen`) exist precisely so the dependency points inward.
- `shared/ui/testing` is a further module under `ui/` outside this documentation partition.

## Global Invariants

- Every Android-dependent module is an `ivy.feature` library: Compose, Paparazzi and Hilt come from
  that convention plugin, not from per-module declarations.
- `data/model` stays Android-free, which is what lets `domain` and `data` unit-test on the JVM.
- Money crosses every boundary as `PositiveValue`/`Value` (amount + `AssetCode`), never as a bare
  `Double`.

## Global Side Effects

- `data/core` owns all persistent state: one SQLite file per profile, two preference stores
  (`SharedPreferences` `ivy_wallet_prefs`, `DataStore` `ivy_wallet_datastore_v1`), user-selected files
  and the network.
- `ui/*` renders, and therefore mutates only Compose state and the navigation back stack.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`
- Per module: `./gradlew :shared:<group>:<module>:testDebugUnitTest`

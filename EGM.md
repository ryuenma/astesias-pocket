# AstesiaWallet (Astesia's Pocket) Blueprint

## System Overview

A sideload-only Android money tracker for two people, forked from Ivy Wallet under
GPL-3.0. `rootProject.name` is `AstesiaWallet`; the shipped application id is
`astesias.pocket` while the Kotlin namespace remains `com.ivy.wallet`. The Gradle
graph declares **34 modules**: one application (`:app`), 18 feature modules, 7
shared libraries, 2 inherited-code modules under `temp/`, and 6 non-shipping
tooling modules (`:ci-actions:*`, plus `:buildSrc`).

Each profile on a device owns its own Room database file, and devices exchange a
**directory of account names** — never balances or transactions — through a
self-hosted PocketBase instance, so that a cross-profile transfer can name a real
destination.

## Local Interface & Invariants

- `settings.gradle.kts` — the module graph itself. It enables typesafe project
  accessors, sets `repositoriesMode = FAIL_ON_PROJECT_REPOS` (a repository declared
  in a subproject build script fails the build rather than silently resolving), and
  `include`s the 34 modules.
- `build.gradle.kts` — applies `ivy.detekt`, `com.jraska.module.graph.assertion`
  (which **asserts** the module dependency graph rather than trusting convention),
  the Gradle wrapper-upgrade plugin whose repo is pinned to `Ivy-Apps/ivy-wallet`,
  and Kover coverage for every subproject. Kover excludes `*Activity`,
  `*.BuildConfig`, Hilt-generated code and anything annotated `@Composable`, so
  coverage numbers describe logic, not UI.
- The root holds no runtime code: applying these two files changes what every
  module compiles to, but performs no work at runtime.

## Subdirectories & System Boundaries

- [app](./app/EGM.md) — Android application module (`astesias.pocket` / namespace `com.ivy.wallet`): Hilt + Compose entry point, aggregates every other module, three build types, Firebase deliberately not applied.
- [buildSrc](./buildSrc/EGM.md) — Gradle convention plugins (`ivy.module`, `ivy.feature`, `ivy.room`, `ivy.hilt`, …) that every module applies; the single lever that changes all 34 modules at once.
- [ci-actions](./ci-actions/EGM.md) — GitHub-workflow tooling modules; never packaged into the app, and still pointed at the upstream `Ivy-Apps/ivy-wallet` repository.
- [feature](./feature/EGM.md) — 18 Compose UI modules, one per screen group; each owns its ViewModel, immutable state and composables, and delegates data access to `shared`.
- [scripts](./scripts/EGM.md) — Eleven root-level shell wrappers around Gradle tasks (tests, lint, detekt, Paparazzi, module graph).
- [shared](./shared/EGM.md) — Platform primitives (`base`), data model + Room/Ktor core (`data`), use cases (`domain`), design system and navigation (`ui`).
- [temp](./temp/EGM.md) — Inherited Ivy Wallet code the fork **keeps and edits**: `:temp:legacy-code` (legacy logic and UI) and `:temp:old-design` (the design system that legacy UI renders through).

## Global Invariants

- **Dependency direction is enforced, not documented.** `shared/base` ← `shared/data/model`
  ← `shared/data/core` ← `shared/domain` ← `shared/ui/*`; `data/core` has **no** edge
  to `domain`. The `module.graph.assertion` plugin in the root build fails the build
  when an edge is added in the wrong direction.
- **Convention plugins govern every module.** `buildSrc` is the only supported place
  to change Kotlin, Compose, Hilt, Room or Paparazzi configuration; editing a single
  module's `build.gradle.kts` to diverge creates a build that no other module shares.
- **Namespaces lag the rebrand on purpose.** The application id is `astesias.pocket`,
  but packages, manifest components and the Hilt graph are `com.ivy.wallet`. Moving
  the namespace is a tree-wide change, not a rename.
- **No telemetry leaves the device.** Firebase, Crashlytics and Firestore are not
  applied, and `google-services.json` is deleted; the only network peer the app can
  reach is the configured sync server.
- **Two profiles, two databases.** One SQLite file per profile (`ivywallet.db` for
  `default`, `so.db` for the partner profile); a file that is shared between profiles
  breaks the isolation the transfer flow depends on.
- **GPL-3.0.** The fork is personal and sideload-only; nothing about it is a
  redistributable product.

## Global Side Effects

- **Room / SQLite** — per-profile local databases under the app's data dir; the ledger,
  directory cache, transfer outbox and pending-bills tables all live here.
- **Network** — HTTPS to the self-hosted PocketBase (`astesias-pb.fly.dev` by default)
  for sign-in, directory publish/fetch, transfer events and confirmations. Cleartext
  HTTP is refused by the release manifest.
- **Background work** — WorkManager runs the transfer sync (15 minutes) and the bill
  reminder; without notification permission those fire silently.
- **Local git history** — the `scripts/baseline*` and unit-test wrappers stage and
  commit artifacts locally on the developer's machine and never push.

## System Verification

- Run: `./gradlew test` (JVM unit tests across all modules), then
  `./gradlew verifyPaparazziDebug` (screenshot goldens).
- `./gradlew detekt` scans the whole repo but is **not** a passing gate today —
  it carries pre-existing findings, so treat it as advisory and compare against the
  files you touched rather than expecting a clean exit.

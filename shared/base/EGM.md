# shared/base — Platform Primitives

## Overview

Android library module `:shared:base` (namespace `com.ivy.base`). It is the bottom of the
`shared/` dependency stack: it depends on **no other project module** — `build.gradle.kts`
declares only the `ivy.feature` convention plugin and a namespace — so every other shared
module may depend on it and it may depend on none of them.

It holds the primitive cross-cutting concerns that would otherwise be duplicated: dispatcher
injection, time abstraction, resource lookup, kotlinx-serialization adapters, the small
`TransactionType`-family enums, and the `com.ivy.base.legacy` quarantine for pre-EGM code.

## Interface

- `com.ivy.base.Toaster` — `suspend fun show(messageId: Int, duration: Int)` / `show(message: String, duration: Int)`.
- `com.ivy.base.TestDispatchersProvider`, `com.ivy.base.TestCoroutineScope` — `@VisibleForTesting` unconfined dispatchers.
- `com.ivy.base.threading.DispatchersProvider` — `main` / `io` / `default` as `CoroutineContext`.
- `com.ivy.base.time.TimeProvider`, `TimeConverter`, `TimeRange` (`INSTANT_MIN_SAFE` / `INSTANT_MAX_SAFE`).
- `com.ivy.base.resource.ResourceProvider` — string lookup indirection.
- `com.ivy.base.di` — `BaseModule`, `BaseHiltBindings`, `KotlinxSerializationModule`, `@AppCoroutineScope`.
- `com.ivy.base.kotlinxserilzation` — `KSerializerInstant`, `KSerializerLocalDateTime`, `KSerializerUUID`.
- `com.ivy.base.model` — `TransactionType`, `LoanRecordType`, plus the mispackaged `IntervalType` / `LoanType`.
- `com.ivy.base.legacy` — deprecated `Transaction`, `SharedPrefs`, `ZipUtils`, `FileUtil`, widget helpers.

## Subdirectories & Boundaries

- [src/](./src/EGM.md) — Gradle source-set container for :shared:base (main production Kotlin + JVM unit tests).

## Invariants

- **Leaf of the module graph.** `shared/base` must not gain a dependency on `shared/domain`,
  `shared/data/core`, `shared/data/model` or `shared/ui/*`; doing so creates a cycle because
  all of them already depend on (or are depended on through) this module.
- Every abstraction here is an **interface plus a Hilt-bound implementation**
  (`DispatchersProvider`, `TimeProvider`, `TimeConverter`, `ResourceProvider`). Production code
  must inject the interface, never `IvyDispatchersProvider` / `DeviceTimeProvider` directly,
  so tests can substitute `TestDispatchersProvider` / `TestTimeConverter`.
- Time is abstracted on purpose: `TimeProvider` is the only legal source of "now"; wall-clock
  reads inside business logic break deterministic tests.
- `INSTANT_MIN_SAFE` / `INSTANT_MAX_SAFE` exist because converting `Instant.MIN`/`MAX` to
  `LocalDateTime` throws `DateTimeException`; `StandardTimeConverter` clamps to them instead of
  propagating. Do not remove the 10-year safety offset.

## Side Effects

- `Toaster.show` posts an Android `Toast` on the main dispatcher (UI side effect).
- `BaseModule` creates a `SupervisorJob`-backed application-lifetime `CoroutineScope`.
- `KotlinxSerializationModule` provides a lenient `Json` with `ignoreUnknownKeys = true`
  process-wide.
- `legacy/SharedPrefs` reads and writes the `ivy_wallet_prefs` `SharedPreferences` file.
- `legacy/FileUtil` and `legacy/ZipUtils` touch external storage, content-provider URIs and the
  file system; `legacy/GlobalUtils.refreshWidget` broadcasts an app-widget update.

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

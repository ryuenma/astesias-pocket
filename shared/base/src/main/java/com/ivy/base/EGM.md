# com.ivy.base — base module root package

## Overview

Root package of `:shared:base`. Holds the two top-level types that do not belong to a narrower
subpackage — `Toaster` (main-thread user messaging) and the `@VisibleForTesting` dispatcher
fixtures — and groups the seven `base` subpackages that keep the module's real abstractions.

## Interface

- `class Toaster @Inject constructor(@ApplicationContext Context, ResourceProvider, DispatchersProvider)`
  - `suspend fun show(@StringRes messageId: Int, duration: Int = Toast.LENGTH_LONG)`
  - `suspend fun show(message: String, duration: Int = Toast.LENGTH_LONG)`
- `object TestDispatchersProvider : DispatchersProvider` — all three contexts are `Dispatchers.Unconfined`; `@VisibleForTesting`.
- `val TestCoroutineScope: CoroutineScope` — `Unconfined + CoroutineName("test")`; `@VisibleForTesting`.
- `TestDispatchersProvider.kt` imports `com.ivy.base.threading.DispatchersProvider`, so this file
  is the module's only cross-subpackage consumer at the top level.

## Subdirectories & Boundaries

- [di/](./di/EGM.md) — Hilt modules for :shared:base: AppCoroutineScope provider, interface-to-impl bindings, and the shared lenient Json instance.
- [kotlinxserilzation/](./kotlinxserilzation/EGM.md) — kotlinx-serialization adapters for Instant, LocalDateTime and UUID (epoch-millis / string primitives); package name is misspelled in source.
- [legacy/](./legacy/EGM.md) — Deprecated pre-EGM compatibility layer: legacy Transaction model, SharedPrefs, widget/file/zip helpers and UTC epoch-milli date utilities.
- [model/](./model/EGM.md) — Cross-module enums (TransactionType, LoanRecordType, IntervalType, LoanType) plus the total LoanRecordType.processByType helper.
- [resource/](./resource/EGM.md) — ResourceProvider abstraction over Context.getString with an Android impl and a throwing test impl.
- [threading/](./threading/EGM.md) — DispatchersProvider abstraction and its production Dispatchers.Main/IO/Default implementation.
- [time/](./time/EGM.md) — TimeProvider/TimeConverter abstractions plus INSTANT_MIN_SAFE / INSTANT_MAX_SAFE overflow guards; single funnel for clock and zone access.

## Invariants

- `Toaster.show` must be called with a main-thread-safe duration constant; the class itself
  switches to `dispatchers.main` before touching `Toast`, so callers may invoke it from any
  dispatcher and must not wrap it in another `withContext(Dispatchers.Main)`.
- `TestDispatchersProvider` and `TestCoroutineScope` are test fixtures that live in `main` so
  other modules' unit tests can use them. They must never be bound in a production Hilt graph.

## Side Effects

- `Toaster.show` displays a `Toast` on the process UI thread.
- `TestCoroutineScope` is a long-lived `CoroutineScope` with no job cleanup; coroutines launched
  on it are never cancelled automatically.

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

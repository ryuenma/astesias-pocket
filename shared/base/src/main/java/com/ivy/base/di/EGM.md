# com.ivy.base.di — base Hilt modules

## Overview

Hilt wiring for `:shared:base`. Provides the application-lifetime coroutine scope, binds the
module's interface/implementation pairs, and supplies the shared `Json` instance. All modules are
`@InstallIn(SingletonComponent::class)`, so they apply to every consumer of the app graph.

## Interface

- `object BaseModule` — `@Provides @AppCoroutineScope fun provideApplicationCoroutineScope(DispatchersProvider): CoroutineScope`.
- `annotation class AppCoroutineScope` — `@Qualifier`, `AnnotationRetention.BINARY`.
- `interface BaseHiltBindings` — `@Binds` for:
  - `IvyDispatchersProvider` → `DispatchersProvider`
  - `DeviceTimeProvider` → `TimeProvider`
  - `StandardTimeConverter` → `TimeConverter`
  - `AndroidResourceProvider` → `ResourceProvider`
- `object KotlinxSerializationModule` — `@Provides fun provideJson(): Json`.

## Invariants

- `BaseHiltBindings` must stay an `interface` module (`@Binds`); converting it to `object` with
  `@Provides` would require constructing the impls by hand and break constructor injection.
- Exactly one binding per interface may exist in the graph. `TestDispatchersProvider` and
  `TestTimeConverter` must never be added here — tests substitute them with `@TestInstallIn` or
  direct construction, because a second binding makes Hilt fail at compile time.
- The provided `CoroutineScope` is qualified with `@AppCoroutineScope`; unqualified
  `CoroutineScope` is deliberately not bound so that a consumer cannot accidentally inject an
  application scope where a screen scope is meant.

## Side Effects

- `provideApplicationCoroutineScope` allocates a `SupervisorJob()` that lives for the whole
  process; coroutines launched on it survive screen destruction.
- `provideJson` allocates one process-wide lenient `Json` (`ignoreUnknownKeys = true`,
  `isLenient = true`), so unknown fields in remote payloads are silently dropped everywhere.

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

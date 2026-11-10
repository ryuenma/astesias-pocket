# com.ivy.base.threading — dispatcher injection

## Overview

Coroutine-dispatcher injection point. Every suspend function in the codebase resolves its
dispatchers from a `DispatchersProvider` so tests can run unconfined instead of sleeping.

## Interface

- `interface DispatchersProvider { val main: CoroutineContext; val io: CoroutineContext; val default: CoroutineContext }`
- `class IvyDispatchersProvider @Inject constructor() : DispatchersProvider` — `Dispatchers.Main` / `.IO` / `.Default`.

## Invariants

- Properties are `CoroutineContext`, not `CoroutineDispatcher`: a provider may legitimately
  return a context carrying a `CoroutineName` or job, and `withContext` accepts either.
- `IvyDispatchersProvider` is the only binding registered (`BaseHiltBindings`); tests use
  `TestDispatchersProvider` from `com.ivy.base` instead of installing a second binding.
- Dispatchers are resolved **at call time** through the injected instance; do not capture
  `Dispatchers.IO` in a field of a class that is expected to be testable.

## Side Effects

- None.

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

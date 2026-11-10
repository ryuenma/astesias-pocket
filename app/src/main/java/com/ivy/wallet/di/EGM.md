# App DI Bindings

## Overview
Hilt binding module for the `:app` module. It is the seam where the app-level implementation of a
domain interface is bound into the singleton graph.

## Interface
- `@Module @InstallIn(SingletonComponent::class) abstract class AppBindingsModule`
- `@Binds abstract fun appStarter(appStarter: IvyAppStarter): AppStarter`

## Invariants
- `@InstallIn(SingletonComponent::class)`: the binding lives for the whole process — a second binding
  of `AppStarter` anywhere else would fail the graph at compile time.
- Abstract `@Binds` (not `@Provides`): Hilt constructs `IvyAppStarter` via its `@Inject` constructor.
- `AppStarter` is declared in `:shared:domain`; the app is the only module allowed to pick the
  implementation.

## Side Effects
- None at runtime beyond graph construction; the bindings themselves perform no IO.

## Verification
- Run: `./gradlew :app:testDebugUnitTest` (Hilt graph validation happens at compile time)

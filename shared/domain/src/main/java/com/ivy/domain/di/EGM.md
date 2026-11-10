# com.ivy.domain.di — domain bindings

## Overview

Binds the domain module's single interface/implementation pair and exposes the `Features` graph to
callers that cannot inject it.

## Interface

- `interface IvyCoreBindingsModule` — `@Binds fun bindFeatures(features: IvyFeatures): Features`.
- `@EntryPoint @InstallIn(SingletonComponent::class) interface FeaturesEntryPoint` — `fun getFeatures(): Features`.

## Invariants

- `Features` has exactly one binding. Adding a test fake here produces a duplicate-binding compile
  failure, not a test-local override; use `@TestInstallIn` instead.
- `FeaturesEntryPoint` exists so non-injectable call sites (Compose previews, instrumentation entry
  points) can reach the graph. It is an `@EntryPoint`, not a module: do not add `@Provides` to it.
- `IvyFeatures` is a concrete `@Singleton`; the module binds it, so consumers inject `Features` and never
  the implementation.

## Side Effects

- Adds one binding to the singleton Hilt graph.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

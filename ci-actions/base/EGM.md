# ci-actions:base

## Overview
Shared library for the repository's GitHub automation executables. It is a plain JVM Kotlin module
(`id("ivy.script")`, i.e. `kotlin("jvm")` + kotlinx.serialization + Arrow) whose only exported
dependency is the Ktor bundle; the four action modules depend on it and nothing else.

`build.gradle.kts` applies `ivy.script` and declares `api(libs.bundles.ktor)` — so the convention
plugin `buildSrc/src/main/kotlin/ivy.script.gradle.kts` (JVM toolchain, context receivers, Arrow and
Kotlin bundles) constrains how this module and every other ci-action compiles.

## Interface
- Gradle project `:ci-actions:base`, plugin `ivy.script`, `api(libs.bundles.ktor)`
- Source tree under `./src/main/kotlin/` — CLI-argument parsing, Arrow/Either helpers, the GitHub
  service and its DTO models, Ktor bootstrap
- `./src/test/kotlin/` holds the module's tests (excluded from contracts)
- Contracts: `./src/EGM.md`

## Invariants
- Consumed only by `:ci-actions:*` modules; it is not shipped in the Android app.
- `api` (not `implementation`) for Ktor: consumers use `HttpClient` types directly.
- Contains no `application` plugin and no `main` — every executable lives in a sibling module.

## Side Effects
- None at configuration time beyond resolving the Ktor dependency bundle.
- `ivy.script` does not raise the test heap (that is `ivy.kotlin-android`'s 2048 MB), so this module's
  tests run with the JVM default heap.

## Verification
- Run: `./gradlew :ci-actions:base:test`

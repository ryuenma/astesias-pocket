# buildSrc — Gradle build logic

## Overview
Included build that provides the repository's convention plugins. `settings.gradle.kts` maps the `libs`
version catalog to `../gradle/libs.versions.toml`; `build.gradle.kts` applies `kotlin-dsl`, adds the
plugin repositories (google, mavenCentral, jitpack, plugin portal), depends on every Gradle plugin the
build applies, and exposes the catalog to precompiled scripts via the
`libs.javaClass.superclass.protectionDomain...` trick.

Because it is `buildSrc`, these plugins are compiled before any module and constrain every module that
applies them.

## Interface
- Gradle modules: this is an implicit included build, not a project in `settings.gradle.kts`
- Version catalog import: `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`
- Plugin repositories: `google()`, `gradlePluginPortal()` (+ jitpack/mavenCentral for the implementation deps)
- Plugin dependencies exposed to precompiled scripts: AGP, Kotlin, Compose compiler, Hilt, detekt,
  kotlinx-serialization, KSP, molecule, Room, Paparazzi, module-graph
- Contracts: `./src/EGM.md`

## Invariants
- Any new Gradle plugin used by a module must be added to the `dependencies` block here, otherwise a
  precompiled script cannot apply it by id.
- `kotlin-dsl` compiles these `.kts` files as Kotlin: a syntax error fails the whole build before any
  module task runs.
- Test tasks use the JUnit platform (`tasks.withType<Test> { useJUnitPlatform() }`).
- The catalog path `../gradle/libs.versions.toml` is relative to this directory — moving either file
  breaks plugin resolution, not just module dependencies.

## Side Effects
- Configures the entire build: every module's Android/Kotlin/lint/dependency setup is produced
  by these plugins.
- Downloads the Gradle plugin artifacts listed above at build time (network on first run).

## Verification
- Run: `./gradlew help`

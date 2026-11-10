# main — production source set

## Overview

Production source set of `:shared:data:core`. Contains a Kotlin root (`java/`) and an
`AndroidManifest.xml` — the only manifest in the `shared/` tree.

## Interface

- `java/` → [./java/EGM.md](./java/EGM.md)
- `AndroidManifest.xml` (180 bytes) — minimal manifest; the module declares its namespace
  `com.ivy.data` in `build.gradle.kts`, so the manifest carries no package attribute of its own.

## Subdirectories & Boundaries

- [java/](./java/EGM.md) — Kotlin source root of :shared:data:core.

## Invariants

- The manifest stays minimal: this is a library module, and anything merged into the app manifest
  from here affects every consumer.

## Side Effects

- Manifest entries are merged into the consuming app's manifest at build time.

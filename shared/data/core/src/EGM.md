# src — shared/data/core source sets

## Overview

Source-set container for `:shared:data:core`. Uniquely among the shared modules this one has three
source sets: `main/`, `test/` (JVM unit tests) and `androidTest/` (instrumented migration and
real-backup tests).

## Interface

- `main/` → [./main/EGM.md](./main/EGM.md)
- `test/` → [./test/EGM.md](./test/EGM.md)
- `androidTest/java/com/ivy/data/` — `backup/BackupDataUseCaseAndroidTest`, `backup/RealBackupImportTest`,
  `db/IvyRoomDatabaseMigrationTest`, `db/Migration128to129Test`, `db/Migration131to132Test`,
  `db/Migration132to133Test`, `usecase/SyncExchangeRatesUseCaseTest`, plus
  `assets/backups/450-150.json|zip`. Not owned by this partition; no contract file.
- `schemas/` at module root supplies `androidTest` assets via the `ivy.room` plugin
  (`getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))`).

## Subdirectories & Boundaries

- [main/](./main/EGM.md) — Production source set of :shared:data:core with a Kotlin root and the only AndroidManifest.xml under shared/.
- [test/](./test/EGM.md) — Unit-test source set of :shared:data:core, running on the JVM against fake DAOs with resources and snapshot directories.

## Invariants

- The `androidTest` source set is only present because `ivy.integration.testing` is applied; a
  migration cannot be verified on the JVM, so every new `Migration` needs an entry here.

## Side Effects

- None.

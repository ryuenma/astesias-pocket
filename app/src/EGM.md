# app — source sets

## Overview
Variants of the app module. All product code lives in `main`; the other variants only contribute
manifest overrides.

## Subdirectories & Boundaries
- `main`: `./main/EGM.md` — sources, resources and manifest of the app
- `debug`: contains only `AndroidManifest.xml`, which sets `usesCleartextTraffic="true"`
  (`tools:replace`) so a local PocketBase can be reached over plain HTTP. No contract — manifest-only, no code
- `release`: contains no buildable source (no manifest, no Kotlin) and contributes nothing to the
  compile; ignore it. No contract

## Verification
- Run: `./gradlew :app:assembleDemo`

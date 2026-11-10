# App UI

## Overview
App-module Composables that are not owned by a feature module. Today this is only the app-lock screen,
which is application-shell UI rather than product UI.

## Subdirectories & Boundaries
- `applocked`: `./applocked/EGM.md` — `AppLockedScreen`: the full-screen lock UI shown before the nav graph

## Verification
- Run: `./gradlew :app:testDebugUnitTest`

# Legacy Main Source Root

## Overview
The Java/Kotlin source root of `:temp:legacy-code`. Every production file of the module sits below it, under `com/ivy`.

## Subdirectories & Boundaries
- [com](./com/EGM.md) - Package chain (`com`) under the main source root.

## Invariants
- `src/main/java` (not `src/main/kotlin`) is the root even though the sources are Kotlin - the Gradle Android plugin adds it by default, so no `sourceSets` block is needed.
- `src/main/res` is not part of this contract: this module contributes no resources beyond what the source references.

## Side Effects
- None.

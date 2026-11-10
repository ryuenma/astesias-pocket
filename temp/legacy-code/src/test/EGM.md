# Legacy Module Test Source Set

## Overview
JVM unit tests for `:temp:legacy-code`. They run on the JVM (`testDebugUnitTest`), which is why the classes under test are the decision/logic seams rather than the `CoroutineWorker`s or UI.

## Subdirectories & Boundaries
- [java](./java/EGM.md) - Test source root of `:temp:legacy-code`.

## Invariants
- Test files are excluded from the EGM contract boundary (`*Test.kt`, `Fake*.kt` fixtures) - they are documented for navigation, not as API.
- Anything that needs a real `Context`, a device or Robolectric cannot live here; those tests belong in a device/UI test source set.

## Side Effects
- None at runtime; the tests are not shipped.

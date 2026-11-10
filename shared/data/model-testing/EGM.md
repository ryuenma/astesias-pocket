# shared/data/model-testing — shared test fixtures

## Overview

Test-only module (namespace `com.ivy.data.model.testing`) that publishes reusable fixtures —
arbiters and assertions for the domain model — so `shared/data/core` and `shared/domain` unit
tests do not re-implement them. It is consumed as `testImplementation(projects.shared.data.modelTesting)`.

## Interface

- `build.gradle.kts` — `ivy.feature` plugin, `implementation(projects.shared.data.model)`,
  `implementation(libs.bundles.testing)` (kotest property testing and friends).
- Published content: arbiters and helpers for the model types, used by
  `shared/domain/src/test/java/com/ivy/domain/ArbTransactionsUtil.kt` and
  `shared/data/core/src/test/java/com/ivy/data/ArbTransactionEntity.kt`.
- No `src/` directory is owned here by this partition; its sources are not part of this
  documentation pass.

## Invariants

- **Never a production dependency.** `model-testing` must only appear as `testImplementation` /
  `testFixtures`; shipping kotest or arbiters in the app would balloon the APK and pull a test
  framework into the release graph.
- It may depend on `model` but not on `data/core` or `domain`, or the test modules gain a cycle.

## Side Effects

- None at module level; fixture generators create in-memory data only.

## Verification

- Run: `./gradlew :shared:data:model-testing:testDebugUnitTest`

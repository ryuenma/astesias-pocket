# ci-actions

## Overview
Kotlin/JVM Gradle modules that implement the repository's GitHub automation: assigning issues,
welcoming new issues, checking PR descriptions and enforcing Compose stability. Each module is a
standalone `application` built on the shared `base` library and is executed from a workflow with
`./gradlew :ci-actions:<module>:run --args=...`.

They are build-time/CI tooling — nothing here is packaged into the Android app.

## Subdirectories & Boundaries
- `base`: `./base/EGM.md` — shared library (`:ci-actions:base`): argument DSL, Arrow helpers, GitHub service, Ktor scope
- `compose-stability`: `./compose-stability/EGM.md` — `:ci-actions:compose-stability` — parses Compose compiler reports, fails on new unstable composables
- `issue-assign`: `./issue-assign/EGM.md` — `:ci-actions:issue-assign` — assigns an issue when a contributor comments the take-intention phrase
- `issue-create-comment`: `./issue-create-comment/EGM.md` — `:ci-actions:issue-create-comment` — posts the welcome/guidelines comment on a new issue
- `pr-description-check`: `./pr-description-check/EGM.md` — `:ci-actions:pr-description-check` — validates a base64-encoded PR description

## Verification
- Run: `./gradlew :ci-actions:base:test :ci-actions:issue-assign:test :ci-actions:issue-create-comment:test :ci-actions:compose-stability:test :ci-actions:pr-description-check:test`
  (this is exactly what `.github/workflows/ci_actions_test.yml` runs)

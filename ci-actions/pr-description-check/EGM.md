# ci-actions:pr-description-check

## Overview
GitHub Action that rejects pull requests whose description does not follow the repository PR template.
The description is passed base64-encoded as the single `--args` value; the action runs a list of
analyzers and fails the check when any of them reports a problem.

`build.gradle.kts` applies `ivy.script` + `application` with `mainClass = "ivy.automate.pr.MainKt"`.

## Interface
- Gradle project `:ci-actions:pr-description-check`, task `run`
- Workflow invocation: `./gradlew :ci-actions:pr-description-check:run --args="<base64 description>"`
- Contracts: `./src/EGM.md`

## Invariants
- Exactly one argument (base64) is required; the workflow env `PR_DESCRIPTION` is pre-encoded.
- Currently one rule: the description must contain `Closes #<n>` or `Closes N/A` (case-insensitive,
  HTML comments ignored).
- Analyzers are additive: adding a rule means appending to the list in `Main.kt`.
- Needs no PAT — this action only reads its argument and never calls GitHub.

## Side Effects
- Prints the decoded description and the verdict to stdout; throws `PRDescriptionError` (non-zero exit)
  to fail the workflow.
- No network access and no repository mutation.

## Verification
- Run: `./gradlew :ci-actions:pr-description-check:test`

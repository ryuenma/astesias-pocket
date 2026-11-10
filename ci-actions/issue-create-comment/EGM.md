# ci-actions:issue-create-comment

## Overview
GitHub Action that posts the standard welcome comment on a freshly opened issue: thanks the author,
links the contribution guidelines and tags the maintainer for review/approval.

`build.gradle.kts` applies `ivy.script` + `application` with `mainClass = "ivy.automate.issue.create.MainKt"`.

## Interface
- Gradle project `:ci-actions:issue-create-comment`, task `run`
- Workflow invocation: `./gradlew :ci-actions:issue-create-comment:run --args='issueNumber=<n> gitHubPAT=<token>'`
- Contracts: `./src/EGM.md`

## Invariants
- Exactly one comment per run; the action is not idempotent, so the workflow must only trigger on
  `issues: opened`.
- The comment text is generated from the issue's creator, so a fetch failure aborts before commenting.
- Shares `:ci-actions:base` with the other actions and therefore the same upstream-repo base URL.

## Side Effects
- Fetches the issue and writes one public comment on the upstream repository using the PAT secret.

## Verification
- Run: `./gradlew :ci-actions:issue-create-comment:test`

# ci-actions:issue-assign

## Overview
GitHub Action that assigns an issue to the contributor who asks for it. It runs on issue comments,
reads the last comment, and decides between assigning, warning that someone else has it, warning that
the issue is not approved yet, or doing nothing.

`build.gradle.kts` applies `ivy.script` + `application` with `mainClass = "ivy.automate.issue.MainKt"`.

## Interface
- Gradle project `:ci-actions:issue-assign`, task `run`
- Workflow invocation: `./gradlew :ci-actions:issue-assign:run --args='issueNumber=<n> gitHubPAT=<token>'`
- Contracts: `./src/EGM.md`

## Invariants
- Decision order is fixed: already assigned → warn `AlreadyTaken`; not labelled `approved` → `NotApproved`;
  otherwise `AssignIssue`. An assignee always wins over the approval check.
- The intent trigger phrases live in `CommentAnalyzer.kt` (`"im on it"`, `"i am on it"`) — the comment
  is lowercased and apostrophes stripped before matching.
- Bot comments (`ivywallet`) are ignored so the automation cannot trigger itself.
- All failure paths surface as `Either<String, String>` and end in `IvyError("TASK FAILED: ...")`.

## Side Effects
- Reads comments/issue/labels of a live GitHub issue and writes back: assigns the issue and posts a
  comment. These are user-visible mutations of the upstream repository.
- Requires a PAT with write access, passed as a workflow secret.

## Verification
- Run: `./gradlew :ci-actions:issue-assign:test`

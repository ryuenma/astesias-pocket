# GitHub Domain Models (automation)

## Overview
Value types used by the `ci-actions` GitHub automation to describe issues, comments, labels and
users. Every string that reaches the GitHub REST API is wrapped in a private-constructor value
class validated at construction time, so an invalid token (blank, non-numeric issue number) cannot
reach the network layer at all.

## Interface
- `data class GitHubIssue(val number: GitHubIssueNumber, val creator: GitHubUser, val assignee: GitHubUser?)`
- `data class GitHubComment(val author: GitHubUser, val text: String)`
- `data class GitHubLabel(val name: GitHubLabelName)`
- `data class GitHubUser(val username: GitHubUsername)`
- `@JvmInline value class GitHubIssueNumber(val value: String)` — companion implements `Exact<String, GitHubIssueNumber>`
- `@JvmInline value class GitHubLabelName(val value: String)` — non-blank, trimmed
- `@JvmInline value class GitHubUsername(val value: String)` — non-blank, trimmed
- `@JvmInline value class GitHubPAT(val value: String)` — non-blank, trimmed
- `@JvmInline value class NotBlankTrimmedString(val value: String)` — non-blank, trimmed
- Each companion: `override val exactName: String`, `fun Raise<String>.spec(raw): T`, inherited
  `from(raw): Either<String, T>` and `operator fun invoke(raw): T` from `ivy.automate.base.Exact`

## Invariants
- Constructors are `private`: instances exist only through `Exact.from` (Either) or `invoke` (throws
  `ExactError`), so every instance is already trimmed and non-blank.
- `GitHubIssueNumber` additionally rejects any non-digit input (`"Must contain only digits"`).
- Error strings produced by `from` are prefixed with `"<exactName> error: "`.
- These are immutable value classes; `GitHubIssue.assignee` is deliberately nullable (an issue may be
  unassigned), `creator` is not.

## Side Effects
- None. Pure in-memory value types with no IO, no logging and no network access.

## Verification
- Run: `./gradlew :ci-actions:base:test`

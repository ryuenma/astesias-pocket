# ivy.automate.base.github

## Overview
GitHub REST layer for the automation: the `GitHubService` port, a Ktor-backed implementation, and the
`issueNumber`/`gitHubPAT` argument parser every action reuses. Only the endpoints the actions need are
wrapped — issue, labels, comments, comment-post and assign.

## Interface
- `interface GitHubService`:
  - `suspend fun fetchIssue(issueNumber): Either<Throwable, GitHubIssue>`
  - `suspend fun fetchIssueLabels(issueNumber): Either<Throwable, List<GitHubLabel>>`
  - `suspend fun fetchIssueComments(issueNumber): Either<Throwable, List<GitHubComment>>`
  - `suspend fun commentIssue(pat, issueNumber, text: NotBlankTrimmedString): Either<Throwable, Unit>`
  - `suspend fun assignIssue(pat, issueNumber, assignee: GitHubUsername): Either<Throwable, Unit>`
- `class GitHubServiceImpl(ktorClient: HttpClient) : GitHubService` — DTOs (`IssueDto`, `LabelDto`,
  `CommentDto`, `UserDto`, `CommentPayload`, `AssigneesPayload`) are nested `@Serializable` types
- `data class GitHubIssueArgs(val pat: GitHubPAT, val issueNumber: GitHubIssueNumber)`
- `fun parseArgs(argsList: List<String>): Either<String, GitHubIssueArgs>`
- Subdirectories: `./model/EGM.md`

## Invariants
- Base URL is the constant `https://api.github.com/repos/Ivy-Apps/ivy-wallet` — the automation targets
  the upstream repository, not this fork's remote.
- Bearer header is only attached to mutating calls (`commentIssue`, `assignIssue`); reads are
  unauthenticated and therefore rate-limited.
- Every method returns `Either<Throwable, _>` via `catchIO`; a non-2xx response becomes a failure
  through `requireSuccess()` (`require(status.isSuccess())`), so callers see an `IllegalArgumentException`.
- `parseArgs` requires both `gitHubPAT` and `issueNumber`; each is validated through its `Exact`
  companion, so a blank token or non-digit issue number fails before any request.
- DTO → domain conversion (`UserDto.toDomain`) re-validates the login, so an unexpected blank login
  becomes a left value rather than a `GitHubUsername("")`.

## Side Effects
- Outbound HTTPS to `api.github.com` (GET issue/labels/comments; POST comment; POST assignees).
- Writes a comment / assigns a user on the target repository — these are visible, hard-to-undo actions
  on a real issue.
- Executes network calls on `Dispatchers.IO` (via `catchIO`).

## Verification
- Run: `./gradlew :ci-actions:base:test`

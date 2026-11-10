# Issue Created Comment Action

## Overview
GitHub Action body that runs when an issue is opened. It fetches the issue and posts the standard
"thank you for raising" comment that links the contribution guidelines and tags the maintainer for
review. Registered in `settings.gradle.kts` as `:ci-actions:issue-create-comment` and invoked by the
`issue_created` workflow with `--args='issueNumber=<n> gitHubPAT=<token>'`.

## Interface
- `fun main(args: Array<String>)` — `runBlocking`, builds a `Context(GitHubService)` inside
  `ktorClientScope`, executes and prints `[ISSUE-ASSIGN] TASK SUCCESSFUL|FAILED: ...`
- `data class Context(val gitHubService: GitHubService) : GitHubService by gitHubService`
- `suspend fun execute(argsArr: Array<String>): Either<String, String>` (private)
- `fun commentText(issue: GitHubIssue): String` — public, pure; greeting, guidelines link, `@${Constants.IVY_ADMIN}` tag
- `suspend fun comment(args: GitHubIssueArgs, text: String): String` (private)

## Invariants
- Arguments must be supplied as `key=value` pairs: `issueNumber` and `gitHubPAT`, both required —
  a missing key fails the `Either` chain with `"Argument '<key>' is missing."`.
- `main` throws `IvyError("TASK FAILED: ...")` on the left side, which is what makes the CI step exit
  non-zero; success prints `TASK SUCCESSFUL: Commented on Issue #N`.
- The log prefix is the string literal `[ISSUE-ASSIGN]`, copied from the assign action — it does not
  identify this action.
- `commentText` is pure and total: given any `GitHubIssue` it returns a non-blank comment.

## Side Effects
- One `GET /repos/Ivy-Apps/ivy-wallet/issues/{n}` and one `POST .../comments` against the GitHub API
  with the supplied PAT as a bearer token.
- Prints to stdout (log consumed by GitHub Actions).
- Throws on failure, terminating the process.

## Verification
- Run: `./gradlew :ci-actions:issue-create-comment:test`

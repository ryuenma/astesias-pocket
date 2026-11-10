# ivy.automate.issue (assign action)

## Overview
The issue-assign action's logic, split into three concerns: recognise intent from the last comment
(`CommentAnalyzer`), decide what to do from the issue's current state (`DetermineAction`), and perform
the resulting write plus comment (`ActionExecutor`). `Main.kt` wires them into a single
`Either<String, String>` pipeline over `GitHubService`.

## Interface
- `fun main(args: Array<String>)` — builds `Context(GitHubServiceImpl(...))` inside `ktorClientScope`, prints `[ISSUE-ASSIGN] ...`
- `data class Context(val gitHubService: GitHubService) : GitHubService`
- `sealed interface CommentIntention` — `TakeIssue(user)`, `Unknown`
- `val TakeIssueIntentionPhrases: List<String>` = `["im on it", "i am on it"]`
- `fun analyzeCommentIntention(comment: GitHubComment): CommentIntention`
- `sealed interface Action` — `DoNothing(issueNumber)`, `AssignIssue(issueNumber, user)`, `NotApproved(user, issueNumber)`, `AlreadyTaken(user, issueNumber, assignee)`
- `context(GitHubService) suspend fun determineAction(args: GitHubIssueArgs): Either<String, Action>`
- `context(GitHubService) suspend fun Action.AssignIssue.execute(args)`, `Action.NotApproved.execute(args)`, `Action.AlreadyTaken.execute(args)`: `Either<String, String>`

## Invariants
- Only the **last** comment is inspected; if its author is `Constants.IVY_BOT_USERNAME` the action is
  `DoNothing`, so bot replies cannot re-trigger assignment.
- Precedence is `AlreadyTaken` → `NotApproved` → `AssignIssue`; the approval label is the literal
  `"approved"`.
- Every executed action ends by posting a comment (including the refusal paths) and returns the comment
  text as the `Either` right value.
- Success path assigns then comments; a failed assign aborts before the comment.
- All comment bodies include `Constants.CONTRIBUTING_URL`; refusal texts warn the user with ⚠️.

## Side Effects
- GET comments, GET issue, GET labels; POST assignees and POST comment on the live repository.
- Writes to a real issue (assignment + comment) using the PAT from `--args`.
- Prints `[ISSUE-ASSIGN] TASK SUCCESSFUL|FAILED` to stdout and throws `IvyError` on failure.

## Verification
- Run: `./gradlew :ci-actions:issue-assign:test`

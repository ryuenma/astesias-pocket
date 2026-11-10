# PR Description Check Action

## Overview
Validates the text of a pull request description against the repository PR template. The description
arrives base64-encoded as the single CLI argument and is run through a list of
`PRDescriptionAnalyzer`s; today the only rule is that the description must close an issue.

## Interface
- `interface PRDescriptionAnalyzer { fun analyze(prDescription: String): Either<String, Unit> }`
- `class ClosesIssueAnalyzer : PRDescriptionAnalyzer` — companion exposes `MissingClosesProblem`
- `class PRDescriptionError(msg: String) : Exception(msg)`
- `fun main(args: Array<String>)`

## Invariants
- Exactly one argument is required — `require(args.size == 1)` fails the process otherwise.
- The argument is base64; a decode failure is rethrown as `IllegalArgumentException("CI error: Base 64 decoding failed! ...")`.
- `ClosesIssueAnalyzer` strips HTML comments (`<!-- ... -->`) before matching, then accepts
  `closes #<digits>` or `closes N/A` case-insensitively; anything else yields `MissingClosesProblem`.
- Any left value from any analyzer is collected and thrown as one aggregated `PRDescriptionError`
  that embeds the PR-template URL; a clean description prints `All good! ...` and exits 0.

## Side Effects
- Prints the decoded description and the outcome to stdout.
- Throws (non-zero exit) when the description violates the template.
- No network access.

## Verification
- Run: `./gradlew :ci-actions:pr-description-check:test`

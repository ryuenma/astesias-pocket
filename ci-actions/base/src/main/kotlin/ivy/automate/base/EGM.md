# ivy.automate.base

## Overview
Common foundation for the GitHub automation: a tiny validation DSL for CLI arguments, Arrow `Either`
helpers, shared constants, and the error type used to fail an action. All of these are small,
side-effect-free building blocks that the action modules compose into an `Either<String, _>` chain.

## Interface
- `data class IvyError` — `class IvyError(msg: String) : Exception(msg)`; thrown by `Either.getOrThrow`
  and by every `main` to fail the CI step
- `fun List<String>.parseAsMap(): Map<String, String>` — keeps only `key=value` args (exactly one `=`)
- `context(Raise<String>) fun Map<String, String>.ensureArgument(key: String): String` — fails with
  `"Argument '<key>' is missing."`
- `@DslMarker annotation class IvyDsl`
- `suspend fun <A> catchIO(block: suspend () -> A): Either<Throwable, A>` — runs `block` on `Dispatchers.IO` inside `Either.catch`
- `interface Exact<Value, out ExactValue>` — `exactName`, `Raise<String>.spec(raw)`,
  `from(value): Either<String, ExactValue>`, `operator fun invoke(value): ExactValue`
- `class ExactError(msg: String) : IllegalArgumentException(msg)`
- `object Constants` — `IVY_ADMIN`, `ARG_GITHUB_PAT`, `ARG_ISSUE_NUMBER`, `IVY_BOT_USERNAME`,
  `ISSUES_URL`, `CONTRIBUTING_URL`
- Subdirectories: `./github/EGM.md`, `./ktor/EGM.md`

## Invariants
- Argument keys are exactly the strings in `Constants` (`gitHubPAT`, `issueNumber`); actions must not
  hard-code them.
- `parseAsMap` silently drops malformed args — a missing key is detected later by `ensureArgument`,
  so the failure message is always the missing-argument one.
- `Exact.from` prefixes every error with `"<exactName> error: "`; `invoke` throws `ExactError`.
- `catchIO` never lets an exception escape — it becomes `Either.Left`.
- `Constants` is a hard-coded pointer at the upstream `Ivy-Apps/ivy-wallet` repository (issue and
  contributing URLs, bot login `ivywallet`); it is not derived from this fork.

## Side Effects
- `catchIO` switches to `Dispatchers.IO` for the duration of `block`.
- No IO of its own: no network, no disk, no logging.

## Verification
- Run: `./gradlew :ci-actions:base:test`

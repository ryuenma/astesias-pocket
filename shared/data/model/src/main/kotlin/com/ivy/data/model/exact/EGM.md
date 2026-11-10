# com.ivy.data.model.exact — Exact validation DSL

## Overview

One tiny interface that every validated primitive implements. It centralises the
"construct-or-fail" and "construct-or-throw" duality so each primitive only writes its predicate.

## Interface

- `interface Exact<Value, out ExactValue>`:
  - `val exactName: String`
  - `fun Raise<String>.spec(raw: Value): ExactValue` — the predicate, written against arrow's `Raise`.
  - `fun from(value: Value): Either<String, ExactValue>` — returns `"$exactName error: $msg"` on failure.
  - `fun unsafe(value: Value): ExactValue` — `@Throws(ExactError::class)`; throws on failure.
- `class ExactError(msg: String) : IllegalArgumentException(msg)`.

## Invariants

- `spec` must be written with `ensure(...)`/`bind()` so every failure path composes; throwing
  inside `spec` would escape `either {}` as an exception instead of a typed error.
- The error message format `"<exactName> error: <reason>"` is user-visible in developer-facing
  logs and asserted in tests — keep `exactName` accurate (note `IconAsset` deliberately reports
  `"IconId"` for historical compatibility).
- `unsafe` is the only member allowed to throw; call sites that can recover must use `from`.

## Side Effects

- None other than the `ExactError` throw from `unsafe`.

## Verification

- Run: `./gradlew :shared:data:model:testDebugUnitTest`

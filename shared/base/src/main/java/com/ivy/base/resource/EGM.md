# com.ivy.base.resource — string resource indirection

## Overview

One-method indirection over `Context.getString` so that non-Android code and unit tests can
resolve strings without a `Context`. This is what lets modules below `app/` stay testable on the
JVM.

## Interface

- `interface ResourceProvider` — `fun getString(@StringRes resId: Int): String`,
  `fun getString(@StringRes resId: Int, vararg args: Any): String`.
- `class AndroidResourceProvider @Inject constructor(@ApplicationContext Context) : ResourceProvider`
  — delegates straight to `context.getString`.
- `class TestResourceProvider : ResourceProvider` — `fun putString(@StringRes resId, value)`,
  throws `TestStringNotFoundException` for unknown ids.
- `class TestStringNotFoundException(@StringRes val stringRes: Int) : IllegalStateException`.

## Invariants

- `ResourceProvider` is bound to `AndroidResourceProvider` in `BaseHiltBindings`; consumers inject
  the interface only, otherwise unit tests need a `Context`.
- `TestResourceProvider.getString(resId, *args)` uses `String.format`, so a test string must use
  `%s`-style placeholders rather than Android's default formatting; the source carries a TODO
  that this path is untested.
- `TestResourceProvider` throws instead of returning a placeholder — a missing string in a test is
  meant to fail loudly.

## Side Effects

- `AndroidResourceProvider` reads the application `Resources` table (no mutation).

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

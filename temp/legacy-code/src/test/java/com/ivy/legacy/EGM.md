# Legacy Module Tests

## Overview
The JVM unit tests of `:temp:legacy-code`, in package `com.ivy.legacy`. They exercise the parts of the legacy stack that can run without an Android device.

## Interface
- `LogoutLogicTest` - asserts `LogoutLogic.logout()` clears the sync-side tables.
- `notification` - the bill-reminder decision tests and their fake store.
- Mocking is MockK (`mockk(relaxed = true)`), assertions are Kotest matchers, coroutines run under `runTest`.

- [notification](./notification/EGM.md) - Bill-reminder decision tests and the in-memory pending-bill store they run against.

## Invariants
- Test-only sources: they are not part of the production contract and must never be referenced from `main`.
- `LogoutLogicTest` constructs `LogoutLogic` with **every** dependency explicitly, so adding a constructor parameter to `LogoutLogic` breaks this test at compile time - that is the intended guard against silently skipping a table on logout.
- Relaxed mocks make missing stubs invisible: a test here proves which methods were called, not what they returned.

## Side Effects
- Test-only.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

## Subdirectories & Boundaries

- [`notification`](./notification/EGM.md)

# Android Notification Stack

## Overview
The Android side of notifications: what a notification is, which channel it goes to, whether the app is allowed to post at all, and how posting failures are reported back to the caller.

## Interface
- `IvyNotification(...)` - the payload type (`IvyNotification.kt`).
- `IvyNotificationChannel` - enum of channel definitions with their ids and importance.
- `NotificationPermissionState` (`NotRequired` / granted / denied / never-asked) and `NotificationPermissionGate` (`@Singleton`, `@Inject`) - reads and records the `POST_NOTIFICATIONS` grant.
- `NotificationResult` (`BlockedByPermission`, plus failure outcomes) and `NotificationService` (`@Inject`) - posts a notification and reports the outcome.

## Invariants
- `POST_NOTIFICATIONS` only exists from API 33; below that, notifications are granted at install and the gate must report `NotRequired` rather than a denial.
- `shouldShowRequestPermissionRationale()` returns false in two opposite situations - never asked, and asked-and-permanently-denied - so the gate persists its own "have we asked" flag instead of inferring it.
- `NotificationService` returns a `NotificationResult` on purpose: the previous version returned `Unit` and swallowed every exception, so callers could not learn their notification was dropped. A suppressed post must never be reported as delivered.
- `BlockedByPermission` is a distinct outcome from a generic post failure, because the two need different UI (ask again vs. give up).

## Side Effects
- Reads and writes the persisted "have we asked" flag and the permission state.
- Posts to `NotificationManager` (system side effect, user-visible).
- Creates notification channels.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

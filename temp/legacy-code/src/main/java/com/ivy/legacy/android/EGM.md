# Legacy Android Integration

## Overview
Android-platform integration for the legacy stack. Today it holds one sub-package, `notification`; anything else Android-specific in this module is either in `../notification` (WorkManager scheduling) or in the `:app` module.

## Subdirectories & Boundaries
- [notification](./notification/EGM.md) - `IvyNotificationChannel`, `NotificationPermissionGate`, `NotificationService` and the `IvyNotification` payload type.

## Invariants
- No code lives directly in this directory - it exists to group platform integration.
- The `com.ivy.legacy.android.notification` package here is distinct from `com.ivy.wallet.android.notification` in `../wallet/android/notification`; the notification **id allocator** lives in the latter.

## Side Effects
- None: grouping level.

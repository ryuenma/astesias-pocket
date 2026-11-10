# Notification ID Allocator

## Overview
`IvyNotificationId` - the object that hands out notification ids. Package `com.ivy.wallet.android.notification`.

## Interface
- `object IvyNotificationId` - allocates and tracks notification ids per logical notification.

## Invariants
- A notification id is not cosmetic: `NotificationManager.notify(id, ...)` **replaces** whatever is already posted under that id, and `cancel(id)` removes it. Two features sharing an id means one silently erases the other.
- Ids are allocated rather than guessed - a hardcoded literal elsewhere in the app collides with the allocator's range and produces that silent replacement.
- Cancel and notify must use the same id for the same notification, so the id has to be remembered (or re-derived from stable inputs), not regenerated per post.

## Side Effects
- Posts/cancels notifications through the platform `NotificationManager`.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

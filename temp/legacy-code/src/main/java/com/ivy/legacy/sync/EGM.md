# Transfer Sync

## Overview
PocketBase transfer synchronisation: a 15-minute periodic worker that reconciles the account directory, drains the outbound transfer outbox and pulls incoming transfer events and answers.

## Interface
- `TransferSyncScheduler(...)` - schedules or cancels the periodic `TransferSyncWorker` depending on whether PocketBase sync is configured on this device.
- `TransferSyncWorker(...)` - the periodic worker; obtains its dependencies through `EntryPointAccessors.fromApplication`.
- Package `com.ivy.legacy.sync`.

## Invariants
- Scheduling is configuration-driven: with sync unconfigured the worker must be cancelled, otherwise it wakes every 15 minutes to do nothing.
- The worker uses a Hilt entry point rather than constructor injection because WorkManager instantiates it - adding a dependency means adding it to the entry point, not the constructor.
- Three directions in one worker (directory, outbox, inbox): a failure in one must not silently skip the other two.

## Side Effects
- Registers/cancels periodic WorkManager work.
- Network calls to the PocketBase sync server.
- Database writes for the transfer outbox/inbox and the account directory.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

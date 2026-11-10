# com.ivy.domain.usecase.android — device identity

## Overview

A stable, permission-free device identifier.

## Interface

- `class DeviceIdUseCase @Inject constructor(IvyDataStore)` — `suspend fun getDeviceId(): DeviceId`.
- `@JvmInline value class DeviceId(val value: String)`.

## Invariants

- The id is a **random `UUID` persisted in DataStore** under `device_id`, not an Android hardware
  identifier — that is what makes it permission-free and stable across app restarts.
- `getDeviceId` generates and persists only on first call; concurrent first calls can each generate an
  id, and the last write wins. A caller that must be certain of a single id should serialise the first
  call.
- Clearing app data or reinstalling produces a new id: it identifies an install, not a person, and must
  not be used for anything that has to survive a reinstall.
- `DeviceId` is a plain `@JvmInline` wrapper with no validation (unlike `ProfileId`, which is a
  filesystem boundary).

## Side Effects

- Reads and writes the `device_id` string in the `ivy_wallet_datastore_v1` DataStore.

## Verification

- Run: `./gradlew :shared:domain:testDebugUnitTest`

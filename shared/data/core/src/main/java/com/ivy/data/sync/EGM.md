# com.ivy.data.sync — PocketBase sync

## Overview

The sync stack: configuration and token handling, the PocketBase HTTP clients for directory and
transfer collections, the directory refresh/publish repository, and the transfer outbox that makes
sending idempotent.

## Interface

- `@Singleton class SyncConfig @Inject constructor(SharedPrefs)` — `baseUrl`, `authToken`, `profileId`,
  `isConfigured`, `suspend fun signIn(baseUrl, email, password): DirectoryAuthApi.Result`, plus token
  expiry helpers. Keys: `astesia.sync.base_url`, `astesia.sync.token`, `astesia.sync.profile_id`.
- `object DirectoryAuthApi` — `suspend fun token(baseUrl, email, password): Result`,
  `suspend fun refresh(baseUrl, token): Result`, with `Outcome` (`BAD_CREDENTIALS`, `UNREACHABLE`, …)
  and a `Result(credentials, detail)`.
- `@Singleton class DirectorySyncApi` — `fetchProfiles()`, `fetchDirectory()`, `fetchDirectoryPage(page)`,
  `publishAccount(...)`, `updateAccount(...)`.
- `@Singleton class TransferSyncApi` — `publishEvent(...)`, `publishConfirmation(...)`, `fetchEvents()`,
  `fetchConfirmations()`; `internal fun String.escape()`.
- `@Singleton class DirectorySyncRepository` — `suspend fun refresh(): Boolean`,
  `suspend fun publishLocalAccounts(): PublishReport`.
- `@Singleton class TransferOutboxRepository` — `enqueueEvent(...)`, `enqueueConfirmation(...)`,
  `drain(limit)`: `DrainResult`, `pendingCount()`, `isOwnEvent(correlationId)`, `ownEvent(correlationId)`.
- `object SyncToken` — `expiryMillis(token: String?)`, `isExpired(token, nowMillis, unknownIsExpired = false)`,
  `isExpiringWithin(...)`.
- `internal object PocketBaseError` — `isUniqueViolation(body: String?, vararg fields: String): Boolean`.
- `class SyncConfig`, `SyncToken`, `PocketBaseError` also expose `SyncToken`-based diagnostics through
  `SyncConfig`.

## Invariants

- **Unconfigured by default.** `baseUrl`/`authToken`/`profileId` are all-or-nothing (`isConfigured`),
  because a wrong URL is worse than none: it turns "not set up yet" into a confusing network error.
- **The password is never persisted.** `signIn` exchanges email+password once for the server's
  long-lived token and stores only the token; the class documents that asking for a login instead of a
  pasted token keeps a long opaque string out of screenshots and shell history.
- **Token expiry is checked locally, not by the server.** With an expired token a *write* is refused
  `400 {"data":{},"message":"Failed to create record."}` while a **read returns `200` with
  `totalItems: 0`** — the dangerous signature, because directory refresh looks successful and the picker
  keeps serving stale rows. Local `exp` reading is what makes that detectable before a request is spent.
  `SyncToken.isExpired` returns `true` for an unparseable token only when `unknownIsExpired` is set;
  the default treats it as usable so the server gets to refuse it. `SyncConfig.renewTokenIfNeeded()`
  renews with a 24 h margin and refuses on profile swap. `SyncToken.isExpiringWithin` defaults an
  unreadable token to `true`.
- **PocketBase 400s are ambiguous and must be classified.** `PocketBaseError.isUniqueViolation` accepts
  both `validation_not_unique` (SQL unique index) and `validation_invalid_unique` (field-level
  `unique: true`), and *also* requires one of the collection's indexed `fields` to appear in the body —
  otherwise an unrelated 400 validation error would be mistaken for an idempotent duplicate replay.
- The transfer pipeline is **outbox-based**: `TransferOutboxRepository.enqueue*` records intent, `drain`
  sends a batch (default `DEFAULT_BATCH`) and reports a `DrainResult`, and `isOwnEvent`/`ownEvent` let a
  pulled event be recognised as an echo of this device's own write. `isOwnEvent(correlationId)` returns
  `findState(correlationId) != null` with **no kind or state filter**, so a queued CONFIRMATION row also
  reads as "own"; the answer pull rejects `own.kind != KIND_EVENT` as `NOT_OURS`. `ownEvent(correlationId)`
  returns the retained queue row so the caller can distinguish TRANSFER_EVENT from CONFIRMATION. Skipping
  the outbox re-sends on every retry.
- `sync` depends on Room DAOs, `SharedPrefs` and Ktor, but **not on `shared/domain`** — orchestration
  lives in `com.ivy.domain.usecase.transfer`.
- **Paging invariant.** `DirectorySyncApi.fetchDirectory()` pages with `perPage=200`, bounded by
  `totalItems` and `DIRECTORY_MAX_PAGES`; returns `null` if any page fails. Callers must not assume a
  complete result when `null` is returned.

## Side Effects

- HTTPS calls to the configured PocketBase instance (auth, profiles, directory, transfer events,
  confirmations).
- Persists `astesia.sync.*` keys in `SharedPreferences`.
- Logs failures through `Timber`.
- `TransferOutboxRepository.drain` mutates outbox row status.
- `DirectorySyncRepository.refresh()` **deletes each profile's cached rows and re-inserts** them; the
  short-envelope guard before it is what makes that safe.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

# com.ivy.data.profile — profile isolation

## Overview

Model and machinery for "whose data is on this phone". Each profile owns a **separate database file**,
so isolation is a filesystem property rather than a `WHERE` clause every future query would have to
remember — and listing the database directory is a complete audit of what is stored.

## Interface

- `@JvmInline value class ProfileId private constructor(val value: String)` — `val dbFileName: String`
  (`"$value.db"`), `companion object { val DEFAULT = of("default"); fun of(raw: String): ProfileId;
  fun ofOrNull(raw: String?): ProfileId? }`.
- `data class Profile(val id: ProfileId, val name: String)`.
- `@Qualifier @Retention(BINARY) annotation class ActiveProfileId` — the active id as a plain `String`.
- `@Singleton class ProfileRegistry @Inject constructor(SharedPrefs, FileSystem)` —
  `activeProfileId(): ProfileId`, `activeProfile(): Profile`, `profiles(): List<Profile>`,
  `register(Profile)`, plus `exists`/`setActiveProfile` used by the switcher.
- `@Singleton class ProfileSwitcher @Inject constructor(@ApplicationContext Context, ProfileRegistry)` —
  `switchTo(ProfileId)`, `createAndSwitchTo(Profile)`.
- `@Singleton class DirectoryRepository @Inject constructor(DirectoryAccountDao, SyncConfig)` —
  `suspend fun others(): List<DirectoryAccountEntity>`.
- `internal const val KEY_ACTIVE_PROFILE = "astesia.active_profile"`.

## Invariants

- **`ProfileId` is a filesystem boundary and is validated as an allow-list, not sanitised.**
  `Regex("^[a-z0-9_]{1,32}$")` after `trim().lowercase(Locale.ROOT)`; anything else is rejected
  outright, because `../../databases/other` would open a different database. Display names live in
  `Profile.name` and **never** reach a path.
- `ProfileId.DEFAULT` is the string `"default"` and owns the original Ivy Wallet database file, so an
  existing single-person install keeps its data across the upgrade.
- `DEFAULT` opens `ivywallet.db` (`LEGACY_DB_NAME`), **not** `dbFileName`; `hasLocalData` therefore always
  reports `false` for the profile holding the user's data. A test pins that wrong answer.
- `KEY_ACTIVE_PROFILE` is a shared constant `internal` to this package **so `ProfileRegistry` and
  `ProfileSwitcher` write the same key**. Two private copies would drift and the app would appear to
  switch and then reopen the old profile.
- `ProfileRegistry` is deliberately **synchronous**: `RoomDbModule` reads the active profile during Hilt
  graph construction to decide which file to open, so a suspend or `Flow` API cannot work there.
- `activeProfileId()` reads with the **two-argument** `sharedPrefs.getString(key, null)` overload; the
  single-argument overload throws `IllegalStateException` on a missing key, which would crash the very
  first launch before onboarding.
- An unreadable/absent preference falls back to `ProfileId.DEFAULT` rather than throwing: a bad
  preference must not brick the app.
- `register` updates a name only when the existing name is a **placeholder**; a name the owner typed is
  never clobbered by a later `register`.
- `switchTo` throws `UnknownProfileException` *before* writing the preference, so a failed switch cannot
  leave the app pointing at a profile with no data, and switching to the already-active profile is a
  no-op.
- **`switchTo` writes the id via `SharedPrefs.putString` → `apply()` (async), then `startActivity` +
  `exitProcess(0)`; the pending write can be discarded, relaunching on the old profile with no error.**
- **Switching restarts the process** (`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` then
  `exitProcess(0)`). Swapping the database object behind live DAOs would leave every already-injected
  DAO on the old handle: reads return the previous profile's rows and writes hit a closed database,
  silently. `CLEAR_TASK` also prevents back-navigation into the other profile.
- `DirectoryRepository.others()` resolves "my own" from `SyncConfig` **internally**. It used to be a
  `ProfileId` parameter and every caller passed `"default"` (the *local* id space) while the table is
  keyed by *sync* ids (`"owner"`, `"so"`); the two never intersected, so the destination picker offered
  the device its own accounts. Taking the id from its owner removes the possibility of mixing id spaces.
  **Caveat:** `astesia.sync.profile_id` is one device-wide pref with no profile dimension, while local
  profiles are plural and `findOthers` excludes exactly one value. With a second local profile active,
  `others()` returns that profile's own rows — the sync id is not profile-scoped.
- `DirectoryRepository` intentionally has **no write side**: taking `IvyRoomDatabase` to get
  `withTransaction` would leak Room's `RoomDatabase` into consumer signatures, where Hilt's KSP cannot
  resolve it while Room is `implementation`.

## Side Effects

- Reads and writes `SharedPreferences` (`astesia.active_profile`, the known-profile list).
- `ProfileSwitcher` starts the launcher activity in a cleared task and calls `exitProcess(0)`.
- `ProfileRegistry.profiles()` parses a persisted, separator-delimited string of id/name pairs.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

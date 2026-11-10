# com.ivy.data.di — data Hilt modules

## Overview

The injection graph for the data layer: which database file to open, one provider per DAO, the
DataStore, the Ktor client, and the remote data-source binding.

## Interface

- `object RoomDbModule` — `@Provides @ActiveProfileId fun provideActiveProfileId(ProfileRegistry): String`;
  `@Provides @Singleton fun provideIvyRoomDatabase(@ApplicationContext Context, ProfileRegistry): IvyRoomDatabase`;
  one unscoped `@Provides` per DAO (17 read + 14 write) delegating to the singleton database.
- `object DatastoreModule` — `@Provides fun provideDatastore(@ApplicationContext Context): DataStore<Preferences>`.
- `object KtorClientModule` — `@Provides @Singleton fun provideKtorClient(json: Json): HttpClient`
  with `ContentNegotiation(json)` and `Logging(level = BODY, logger = Timber)`.
- `abstract class RemoteDataSourceModule` — `@Binds` `RemoteExchangeRatesDataSourceImpl` →
  `RemoteExchangeRatesDataSource`.

## Invariants

- **The database is `@Singleton`; the DAOs are not.** Each DAO provider wraps the singleton instance it
  was handed, so every DAO injected once at startup keeps pointing at that object. This is the reason
  a profile switch must restart the process (`ProfileSwitcher`) instead of swapping the database.
- `provideIvyRoomDatabase` calls `profileRegistry.activeProfileId()` **once**, at graph construction.
  The profile cannot change for the lifetime of the graph.
- `@ActiveProfileId` is bound as a plain `String`, deliberately not `ProfileId`, so consumers do not
  need the value class on their classpath just to filter a query.
- `RemoteDataSourceModule` must stay `abstract` — it is a `@Binds` module; converting it to `object`
  with `@Provides` breaks constructor injection of the Ktor client.
- `KtorClientModule` logs at `LogLevel.BODY`: request and response bodies go to logcat via `Timber`.

## Side Effects

- Graph construction opens the SQLite file for the active profile.
- Constructs one process-wide `HttpClient` with a logging interceptor.
- Reads `SharedPreferences` (through `ProfileRegistry`) during injection.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`

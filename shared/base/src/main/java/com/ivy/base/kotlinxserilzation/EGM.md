# com.ivy.base.kotlinxserilzation — serialization adapters

## Overview

`KSerializer` implementations that give `java.time` and `java.util.UUID` a stable primitive wire
representation for kotlinx-serialization. Package name is misspelled (`serilzation`) in the source;
keep it — renaming it is a source change, not a documentation change.

## Interface

- `object KSerializerInstant : KSerializer<Instant>` — descriptor `"Instant"`/`PrimitiveKind.LONG`;
  serializes `value.toEpochMilli()`.
- `object KSerializerLocalDateTime : KSerializer<LocalDateTime>` — descriptor `"LocalDateTime"`/`LONG`;
  uses `Long.epochMilliToDateTime()` and `LocalDateTime.toEpochMilli()` from `com.ivy.base.legacy`.
- `object KSerializerUUID : KSerializer<UUID>` — descriptor `"UUID"`/`PrimitiveKind.STRING`;
  `UUID.fromString` / `toString()`.

## Invariants

- All three are `@Keep` singletons referenced from `@Serializable(with = ...)` annotations, so
  their object names and descriptor names are part of the persisted/JSON wire contract. Renaming
  a descriptor or switching `Instant` from epoch-millis to ISO-8601 breaks cross-profile transfer
  payloads and existing backups.
- `KSerializerLocalDateTime` delegates to the *legacy* UTC epoch-milli helpers rather than
  `TimeConverter`. That is intentional compatibility glue, not an oversight: it keeps
  `LocalDateTime` on UTC while the source comment marks it `TODO: Migrate to Instant`.
- These serializers are pure: they must not read the device time zone. Only `TimeConverter`
  applies `ZoneId.systemDefault()`.

## Side Effects

- None: no I/O, no clock reads, no global state mutation.

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

# com.ivy.data.model.sync — identity contracts

## Overview

The two interfaces every entity uses to expose a stable identity. They exist so sync/transfer
code can be generic over "anything with an id" without depending on concrete entities.

## Interface

- `interface UniqueId { val value: UUID }`
- `interface Identifiable<ID : UniqueId> { val id: ID }`

## Invariants

- `UniqueId.value` is a `UUID` (not a `String`), so ids are type-safe on the wire only after an
  explicit `toString()`. `KSerializerUUID` handles that conversion.
- Entities implement `Identifiable<TheirOwnId>`; an entity must never expose two different ids to
  lookups and to sync, or deduplication across profiles silently fails.
- These interfaces are the only place `java.util.UUID` leaks into the model's public surface.

## Side Effects

- None.

## Verification

- Run: `./gradlew :shared:data:model:testDebugUnitTest`

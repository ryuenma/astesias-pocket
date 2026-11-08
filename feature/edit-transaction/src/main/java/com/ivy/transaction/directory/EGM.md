# Directory Account Picker (com.ivy.transaction.directory)

## Overview
Modal that picks the destination account on another profile for a cross-profile
transfer. These transfers are recorded after the fact, so the picker labels
something that already happened — nothing waits for the other person to accept.

## Interface
- `BoxScope.DirectoryAccountPicker(visible: Boolean, accounts: List<DirectoryAccountEntity>, onSelect: (DirectoryAccountEntity) -> Unit, onDismiss: () -> Unit)` — the only public symbol.
- Private: `EmptyDirectory()`, `DirectoryAccountRow(entry, onClick)`.

## Invariants
- An empty `accounts` list renders `EmptyDirectory`, which names the cause ("this device has not synced with the other phone yet") instead of a bare "no accounts" — those are different conclusions.
- Keys in the list are `accountId`, so rows keep identity across a refresh.
- The list is **fixed** at 320 dp because the parent `IvyModal` is already vertically scrollable — not a stylistic cap on a list.
- Rows are at least 48 dp tall (platform minimum touch target).
- The picker never selects a local account: `accounts` comes from `DirectoryRepository.others()` upstream.
- The caller does **not** persist the chosen account: the ViewModel keeps it in state, nulls the local destination, copies `profileId` into `counterpartProfileId` and publishes. The network publish is missing from Side Effects.
- The quoted empty-state copy is not the shipped string; an unpaired device reaches the same empty state by a second route the doc does not mention.

## Side Effects
- None directly: the composable only reports selection/dismissal through its callbacks. The caller refreshes the directory (`DirectorySyncRepository`) and publishes the chosen `DirectoryAccountEntity` (network publish). The caller does not persist the chosen account locally.

## Verification
- Run: `./gradlew :feature:edit-transaction:testDebugUnitTest` (unit tests in `src/test`).

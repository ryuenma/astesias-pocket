# 2026-11-01 — EGM drift report

Scope: the seven contracts underlying the cross-profile transfer path, reviewed because
the owner reported three live faults on 2026-11-01 (no notification on receipt; an
accepted transfer that never appeared; broken UI copy).

Provenance tags:

- `[mechanical]` — `egm_drift.py` (see §0; **its output is not usable for EGM contracts**)
- `[review]` — a fresh-context drift reviewer comparing doc to code
- `[verified]` — I re-read the code myself and the claim holds

Constraints: read-only. No code and no contract was edited while producing this report.

---

## 0. The mechanical pass is invalid for EGM contracts `[verified]`

`egm_drift.py` produced **95 findings across 45 contracts**. Of five sampled, **five were
artifacts**:

| Finding | Reality |
|---|---|
| `TransferOutboxRepository.drain` appears nowhere | exists, `TransferOutboxRepository.kt:122` |
| `TransactionEntity.counterpartProfileId` appears nowhere | exists, `TransactionEntity.kt:60-61` |
| `AccountDao.findByName` appears nowhere | exists, `AccountDao.kt:31` |
| `CurrencyRepository.setBaseBaseCurrency` appears nowhere | exists (double "Base" is real) |
| `Migration121to122` appears nowhere | the doc *instructs* that it must not exist (numbering gap 121→122) |

Two systematic causes:

1. **Relative links are resolved against the repo root.** Contracts link children as
   `./child/EGM.md`; the checker reports each as "only exists as `EGM.md`". That is 61 of
   the 95 findings.
2. **Qualified names are string-matched.** Any `Class.member` a doc names reports as
   missing, because the literal string `Class.member` appears in no source file. Gradle
   plugin ids (`ivy.room`) and generated accessors (`projects.shared.data.core`) fail the
   same way.

It also cannot run against the working tree at all: it stats the whole tree per symbol, and
the 30 `build/` directories make a single document take 69 s. A source-only mirror (2327
files) runs the same set in 52 s.

**Do not treat this script's output as a checklist for EGM contracts.** The review pass
below is the usable half.

---

## shared/data/core/src/main/java/com/ivy/data/sync/EGM.md

- `isOwnEvent` is described as interchangeable with "echo of my own write". In code it is
  `findState(correlationId) != null` with no kind/state filter, so a queued *confirmation*
  also reads as "own"; the answer pull rejects `own.kind != KIND_EVENT` as `NOT_OURS`. A
  reader implementing the answer path from the doc would misattribute her acceptance.
  `[review]`
- Side Effects omits the only destructive local write: `DirectorySyncRepository.refresh()`
  deletes each profile's cached rows and re-inserts, and the short-envelope guard before it
  is what makes that safe. `[review]`
- No paging invariant stated: `perPage=200`, loop bounded by `totalItems` and
  `DIRECTORY_MAX_PAGES`, `null` if any page fails. `[review]`
- Token bullet omits `renewTokenIfNeeded()` (24 h margin, profile-swap refusal) and
  `isExpiringWithin`'s unreadable-token default of `true`, which contradicts the doc's
  claim that the default treats a token as usable. `[review]`

## shared/data/core/src/main/java/com/ivy/data/db/entity/EGM.md

- "each entity is `@Keep` and `@Serializable`" is false: 12/17 are `@Serializable`, 15/17
  `@Keep`, and the sync-local tables deliberately opt out. Acting on the doc would push
  pending/declined transfer state into user backup files. `[review]`
- "Column names come from `@SerialName`" is false: Room uses the property name (or
  `@ColumnInfo`). Schema `138.json` shows `tags.dateTime` where `@SerialName` says
  `creationTime`. Renaming a property renames a column and forces a migration. `[review]`
- `linkedTransactionId` is described as the idempotency key for the incoming leg. It does
  carry the correlation id, but nothing queries it (`transactions` has `pk:['id']`,
  `indices: []`). The real guard is `pending_transfers.correlationId` PK plus the
  PENDING→terminal transition. `[review]`
- The four transfer tables' primary keys — the feature's actual idempotency story — are
  undocumented. `[review]`

## shared/data/core/src/main/java/com/ivy/data/profile/EGM.md

- "Taking the id from its owner removes the possibility of mixing id spaces" is false:
  `astesia.sync.profile_id` is one device-wide pref with no profile dimension, while local
  profiles are plural and `findOthers` excludes exactly one value. With the second local
  profile active, `others()` returns that profile's own rows. `[review]`
- `DEFAULT` opens `ivywallet.db` (`LEGACY_DB_NAME`), not `dbFileName`; `hasLocalData`
  therefore always reports `false` for the profile holding the user's data, and a test pins
  that wrong answer. `[review]`
- `switchTo` writes the id via `SharedPrefs.putString` → `apply()` (async), then
  `startActivity` + `exitProcess(0)`; the pending write can be discarded, relaunching on the
  old profile with no error. `[review]`

## feature/edit-transaction/src/main/java/com/ivy/transaction/EGM.md

- The doc says the editor "edits cross-profile transfers". It cannot: `display()` never
  restores the directory destination from `counterpartProfileId`, so both sides are null on
  load and every save is refused. `[review]`
- "The correlation id is the leg's own id, so retries are idempotent" is false: `legId` and
  `correlationId` are two independent random UUIDs, and the idempotency gate sits in a
  function that is never reached on the edit path. `[review]`
- "Time of day defaults to noon" is false on create: `DEFAULT_TIME_OF_DAY` is only applied
  in `handleChangeDate`, while the create path stamps `utcNow()`. `[review]`

## feature/edit-transaction/src/main/java/com/ivy/transaction/directory/EGM.md

- The 320 dp is a *fixed* height, mandatory because the parent modal is already vertically
  scrollable — not a stylistic cap on a list. `[review]`
- "The caller persists the chosen account" is wrong: the ViewModel keeps it in state, nulls
  the local destination, copies `profileId` into `counterpartProfileId` and publishes. The
  network publish is missing from Side Effects entirely. `[review]`
- The quoted empty-state copy is not the shipped string, and an unpaired device reaches the
  same empty state by a second route the doc does not mention. `[review]`
- The verification command is vacuous: the module's `src/test` holds only
  `DefaultTimeOfDayTest.kt`. `[review]`

## feature/edit-transaction/src/main/java/com/ivy/transaction/pending/EGM.md

- **"Accept writes a real TRANSFER row" is presented as one step. It is two, in this order:
  `markDecided()` commits `ACCEPTED`, then `transactionDao.save(receiverLeg(...))` writes the
  ledger row, with no enclosing transaction.** `[verified]`
  `shared/domain/.../PendingTransferRepository.kt:112-144`
- **The failure is swallowed and unrecoverable.** The call site wraps the whole thing in
  `runCatching{…}.onFailure{ Timber.e(...) }.getOrNull()`, and release builds plant no
  Timber tree at all. A failed ledger write therefore leaves ACCEPTED with no row, shows
  nothing, and every retry is a no-op because `findPending` only returns PENDING rows.
  `[verified]` `feature/edit-transaction/.../PendingTransfersScreen.kt:87-104`
- The doc's justification — "both re-read the inbox afterwards so the list can never claim a
  decision the database refused" — is false: the re-read is the *inbox*, which now says
  ACCEPTED regardless of whether the ledger write landed. `[verified]`
- The written row's `accountId` is the synthetic
  `nameUUIDFromBytes("pending-transfer-sender:…")`, readable only while
  `counterpartProfileId != null`. `receiverLeg` does set it (`CrossProfileTransfer.kt:95`),
  so this is a fragile coupling rather than a proven defect. `[verified]`
- DB writes are understated: `pullEvents()` inserts `pending_transfers`; `drain()` and
  `enqueueConfirmation` write `transfer_outbox`. `[review]`

## feature/edit-transaction/src/main/java/com/ivy/transaction/answers/EGM.md

- The doc claims the screen distinguishes "still waiting" from "declined and nobody told me".
  It has two states only: recorded rows, or `NoAnswersYet`. `runCatching { pullConfirmations() }`
  discards the returned outcome (NotConfigured / Unreachable / Pulled(0,0,0,0)) because those
  are values, not exceptions — so with an expired token (server answers `200`/`totalItems: 0`)
  an accepted answer renders as "No answers yet" with no error. `[review]`
- `acknowledgeAll()` is an unfiltered `UPDATE … WHERE acknowledged = 0`, run unconditionally
  — including after the read threw, and including rows the list's `INNER JOIN … kind =
  'TRANSFER_EVENT'` excludes while `countUnacknowledged()` still counts them. Badge and list
  can disagree, and opening the screen erases the evidence. `[verified]`
  `shared/data/core/.../WriteTransferConfirmationDao.kt:62-63`
- The named verification command does not exercise this path (same vacuous test module).
  `[review]`

---

## Not drift, but decided by this review

- **No notification path exists for an incoming transfer.** Nothing in
  `shared/domain/.../transfer/`, `feature/.../pending/` or `shared/data/core/.../sync/`
  references notifications; the only notifiers in the repo are `BillReminderWorker`,
  `TransactionReminderWorker` and the `IvyNotification` infrastructure beneath them. The
  recipient was never going to be notified — that is an unbuilt feature, not a broken one.
  `[verified]`
- **The transaction table has no foreign keys** and no indices beyond the primary key
  (`138.json`), so the synthetic sender-account id cannot fail on referential integrity.
  A swallowed failure therefore has to come from the mapper or the DAO, not from an FK.
  `[verified]`

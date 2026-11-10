# com.ivy.base.legacy — deprecated compatibility layer

## Overview

Quarantine for pre-EGM code that other modules still reference. Almost every symbol carries a
`@Deprecated` annotation with an intended replacement, so this package is a **deletion
candidate**, not a place to add features. It survives only because widgets, backup import and
older UI call sites still compile against it.

## Interface

- `data class Transaction(...) : TransactionHistoryItem` (`@Deprecated "Legacy data model. Will be deleted"`),
  `typealias LegacyTransaction = Transaction`. Fields: `accountId`, `type`, `amount`, `toAccountId`,
  `toAmount`, `title`, `description`, `dateTime`, `categoryId`, `dueDate`, `date`, `time`,
  `recurringRuleId`, `paidFor`, `attachmentUrl`, `loanId`, `loanRecordId`, `isSynced`,
  `isDeleted`, `counterpartProfileId`, `tags: ImmutableList<LegacyTag>`, `id`.
- `interface TransactionHistoryItem` (`@Immutable`, deprecated).
- `data class LegacyTag(id: UUID, name: String)`; `enum class Theme { LIGHT, DARK, AUTO, AMOLED_DARK }`.
- `class SharedPrefs @Inject constructor(@ApplicationContext Context)` — `has`, `all`,
  `putInt/putFloat/putDouble/putLong/putString/putBoolean`, `getLong/getInt/getFloat/getBoolean/getString`
  (± default), `remove`, `removeAll`, plus the key constants.
- `object`-less top-level helpers: `appContext: Context` (deprecated `lateinit var`),
  `stringRes(id, vararg args)`, `refreshWidget(Class<*>)`.
- File helpers: `saveFile`, `writeToFile(context, uri, content)`, `Context.getFileName(uri)`.
- `ZipUtils`: `zip(context, zipFile: Uri, files)`, `unzip(context, zipFile: Uri, location)`,
  `unzip(FileInputStream, location)`.
- `DateTimeUtil`: `ZonedDateTime.format(style)`, `Long.epochMilliToDateTime()`,
  `LocalDateTime.toEpochMilli()`.

## Invariants

- **`SharedPrefs` key strings are persistence identifiers.** `"ivy_wallet_prefs"` and every
  `const val` key must never be renamed without a migration; they encode onboarding state,
  notification-permission state and user preferences across app upgrades.
- `NOTIFICATIONS_PERMISSION_ASKED` / `NOTIFICATIONS_BLOCKED` are not cosmetic: the source notes
  `shouldShowRequestPermissionRationale()` cannot distinguish "never asked" from "permanently
  denied", so these flags are the only record of that distinction.
- `Transaction.counterpartProfileId` must stay `null` for an ordinary transfer — the source
  documents that `TransactionMapper` refuses a cross-profile leg unless it is set, and `null`
  is what keeps that check strict.
- `Transaction` intentionally carries default values (`TODO: Remove default values & introduce
  Transaction#dummy()`); removing them breaks every existing call site.
- `epochMilliToDateTime` / `toEpochMilli` are documented in-source as "broken legacy code" yet
  `KSerializerLocalDateTime` depends on them. Do not "fix" them in isolation.
- `appContext` is a mutable global that must be assigned before `stringRes`/`refreshWidget` is
  used; there is no null-safety net.

## Side Effects

- `SharedPrefs` reads/writes the `ivy_wallet_prefs` `SharedPreferences` file with `apply()`.
- `ZipUtils` opens content-provider file descriptors and writes zip entries to disk; `unzip`
  creates directories and files under the caller-supplied location and canonicalises paths to
  guard against zip-slip.
- `FileUtil.saveFile` writes to the public Downloads directory (deprecated, "useless");
  `writeToFile` writes **UTF-16** through a `FileOutputStream`.
- `GlobalUtils.refreshWidget` sends an `ACTION_APPWIDGET_UPDATE` broadcast.
- `appContext` is process-global mutable state.

## Verification

- Run: `./gradlew :shared:base:testDebugUnitTest`

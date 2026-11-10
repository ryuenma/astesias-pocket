# Settings Package (com.ivy.settings)

## Overview
The settings screen: currency and profile name, theme, app lock, notifications,
privacy toggles, month start day, CSV/XLSX export, backup, sync (base URL, profile,
expired-token state), cross-profile transfer destinations and data deletion.

## Interface
- `SettingsEvent` — sealed interface: `SetCurrency`, `SetName`, `ExportToCsv(RootScreen)`, `ExportToXlsx(RootScreen)`, `BackupData(RootScreen)`, `SwitchTheme`, `SetLockApp`, `SetShowNotifications`, `SetHideCurrentBalance`, `SetHideIncome`, `SetTransfersAsIncomeExpense`, `SetStartDateOfMonth`, `DeleteCloudUserData`, `DeleteAllUserData`, `SwitchLanguage`, `NotificationPermissionResult(granted: Boolean)`.
- `SettingsState` — `currencyCode`, `name`, `currentTheme`, `lockApp`, `showNotifications`, `hideCurrentBalance`, `hideIncome`, `treatTransfersAsIncomeExpense`, `startDateOfMonth`, `progressState`, `languageOptionVisible`, `syncBaseUrl`, `syncProfile: String?`, `syncTokenExpired`, `notificationsBlocked`, `notificationsPermanentlyDenied`, `pendingTransfersCount`, `unacknowledgedAnswersCount`.
- `SettingsViewModel` — `@HiltViewModel`, `ComposeViewModel<SettingsState, SettingsEvent>`; injects `SettingsDao`, `IvyWalletCtx`, `LogoutLogic`, `SharedPrefs`, `SyncConfig`, `DirectorySyncRepository`, `TransferSyncScheduler`, `PendingTransferRepository`, `TransferConfirmationRepository`, …; `onStart()`, `refreshNotificationPermission(shouldShowRationale)`, `requiresNotificationPermissionRequest()`.
- `BoxWithConstraintsScope.SettingsScreen()` — entry point (no nav argument); private `UI(...)`.

## Invariants
- `showNotifications` is the **user's** preference; `notificationsBlocked` is the **OS** fact. On API 33+ they legitimately disagree while the user declines, so the switch stays ON and the row admits nothing is arriving.
- `notificationsPermanentlyDenied` is derived from the persisted "have we asked" flag, not from the rationale bit alone.
- `syncTokenExpired` is a real reachable state: an expired sync token makes writes fail with `400` while reads still return `HTTP 200, totalItems: 0`, so the "Connected as …" row would otherwise look healthy. The pairing lasts 120 h and `auth-refresh` refuses an already-expired token.
- `pendingTransfersCount` and `unacknowledgedAnswersCount` are shown on the row even at `0`, so the destinations stay discoverable before they are needed.
- `NotificationPermissionResult` is reported back into the ViewModel rather than acted on in the composable, so the pref, the WorkManager schedule and the blocked flag change in one place.
- `startDateOfMonth` is held as a formatted `String` in state, not an `Int`.

## Side Effects
- Writes: `WriteSettingsDao`/`SettingsDao` (currency, name, start day), `SharedPrefs` (lock app, notifications, hide flags, transfers-as-income-expense, `NOTIFICATIONS_BLOCKED`), `SyncConfig`, `LogoutLogic`.
- Reads: `settingsDao.findFirst()`, `PendingTransferRepository.pending().size`, `TransferConfirmationRepository.unacknowledgedCount()`, `DirectorySyncRepository`.
- Schedules background work: `TransferSyncScheduler` (transfer sync), notification/reminder scheduling.
- Deletes user data (`DeleteAllUserData`, `DeleteCloudUserData`) and exports/backs up files to a caller-chosen location.
- Navigates to the transfer destinations (pending transfers, transfer answers).

## Verification
- Run: `./gradlew :feature:settings:assembleDebug` (this module has no `src/test` source set).

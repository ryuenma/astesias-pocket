# app — application module shell

## Overview
Application-level Kotlin sources for Astesia's Pocket: the `Application` class, the single activity, the
root view model, the Compose nav graph wiring, a quick-settings tile and the migration framework. This
package is the composition root that feature modules plug into; it owns no product feature logic.

## Interface
- `@HiltAndroidApp class IvyAndroidApp : Application(), Configuration.Provider` — injects `HiltWorkerFactory`, publishes `workManagerConfiguration`, sets the legacy `appContext`, plants Timber's `DebugTree` in debug
- `class IvyAppStarter @Inject constructor(@ApplicationContext context)` : `AppStarter` — `getRootIntent()`, `defaultStart()`, `addTransactionStart(type)`; starts `RootActivity` with `NEW_TASK or CLEAR_TASK`
- `@AndroidEntryPoint class RootActivity : AppCompatActivity(), RootScreen` — single `setContent` root; implements `openUrlInBrowser`, `openGooglePlayAppPage`, `shareCSVFile`, `shareFile`, `shareZipFile`, `isDebug`, `buildVersionName`, `buildVersionCode`; hosts date/time pickers, file create/open launchers, biometric prompt and `FLAG_SECURE`
- `@HiltViewModel class RootViewModel(...) : ViewModel` — `appLocked: StateFlow<Boolean?>`, `start(systemDarkMode, intent)`, `lockApp()`, `unlockApp()`, `isAppLocked()`, `isAppLockEnabled()`, `handleBiometricAuthResult()`, inactivity counter; companion `EXTRA_ADD_TRANSACTION_TYPE`, `USER_INACTIVITY_TIME_LIMIT = 60`
- `class PaymentTileService : TileService` — quick-settings tile that starts `RootActivity`
- Subdirectories: `./di/EGM.md`, `./migrations/EGM.md`, `./ui/EGM.md`

## Invariants
- `IvyNavGraph(screen)` is defined one package up (`com.ivy`) and is the only place `Screen`s are mapped
  to Composables; adding a screen means editing that `when` and the `:shared:ui:navigation` model.
- `RootViewModel.appLocked` is `Boolean?`: `null` = unknown yet (render nothing), `true` = locked,
  `false` = unlocked. `isAppLocked()` treats `null` as locked.
- The initial lock state is read from `SharedPrefs.APP_LOCK_ENABLED`, so app lock defaults to off but is
  fail-closed before the preference loads.
- Navigation target after start: `OnboardingScreen` when `SharedPrefs.ONBOARDING_COMPLETED` is false,
  otherwise `MainScreen` — unless the intent carries `EXTRA_ADD_TRANSACTION_TYPE`, which short-circuits
  to `EditTransactionScreen`.
- Bill reminders and transfer sync are re-armed unconditionally on every start (`scheduleBillReminders`);
  idempotency relies on unique work with `REPLACE` in the schedulers.
- `RootActivity.setupApp()` must run before `setContent` — it registers the activity-result launchers and
  installs the `ivyContext` date/time picker hooks (`onShowDatePicker`, `onShowTimePicker`) that feature
  modules call imperatively.
- App-lock inactivity: `onPause` starts a 60 s counter, `onResume` cancels it; `onWindowFocusChanged`
  applies `FLAG_SECURE` whenever the app loses focus with app lock enabled.
- Migrations execute once per install (see `./migrations/EGM.md`).

## Side Effects
- Manages the process-wide `appContext`, WorkManager configuration and Timber logging.
- Writes/reads `SharedPrefs` (app lock, onboarding) and the Preferences DataStore (theme, start day,
  migrations) on `Dispatchers.IO`.
- Schedules notification reminders and reminders/sync work via `BillReminderScheduler`,
  `TransferSyncScheduler` and `TransactionReminderLogic`.
- Starts activities, opens system pickers and share sheets, and launches the OS biometric prompt.
- Mutates the window: edge-to-edge, `FLAG_SECURE` on focus loss.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`

# Legacy Package Root

## Overview
Package `com.ivy.legacy`: the app-level context object, the shared constants, the Compose root helpers the legacy screens use to reach the activity/view, interval and loan display extensions, and `LogoutLogic` - the full data wipe.

## Interface
- `IvyWalletCtx` (`@Singleton`, `@Inject`, extends `com.ivy.design.IvyContext`) - the legacy app context; `@Deprecated("Legacy code. Don't use it, please.")`.
- `Constants` - `URL_TC`, `URL_PRIVACY_POLICY`, `URL_IVY_WALLET_REPO`, `URL_GITHUB_NEW_ISSUE`, `URL_HELP_CENTER`, `URL_IVY_TELEGRAM_INVITE`, `URL_IVY_WALLET_GOOGLE_PLAY`, `CATEGORY_UNSPECIFIED_NAME = "Unspecified"`, `URL_IVY_CONTRIBUTORS`, `URL_GITHUB_SPONSORS`.
- `IvyComposeApp.kt` - `ivyWalletCtx()`, `rootView()`, `rootActivity()`, `rootScreen()`, `IvyWalletComponentPreview`, `IvyWalletPreview`, `appDesign(context)`.
- `IntervalTypeExt.kt` - `IntervalType.forDisplay(intervalN)` and `IntervalType.incrementDate(date, intervalN)`.
- `LoanExt.kt` - `Loan.humanReadableType()`.
- `LogoutLogic` (`@Inject`) - `logout()`, the delete-all-data path.

- [android](./android/EGM.md) - Android integration for the legacy notification stack: channel definitions, permission gate and notification posting.
- [data](./data/EGM.md) - Small immutable view-state carriers (`AppBaseData`, `BufferInfo`, `DueSection`, `EditTransactionDisplayLoan`).
- [datamodel](./datamodel/EGM.md) - Deprecated legacy domain entities (`Account`, `Budget`, `Category`, `Loan`, `Settings`, ...) plus their DAO-entity mappers.
- [domain](./domain/EGM.md) - Domain layer of the legacy stack: FP style actions, pure functions, deprecated logic and shared domain data types.
- [frp](./frp/EGM.md) - Functional-reactive core: `@Pure`/`@Total`/`@Partial`/`@SideEffect` annotations and the `Composition` primitives.
- [legacy](./legacy/EGM.md) - Package chain for the `com.ivy.wallet.ui.theme`-era Compose UI that now sits under `com.ivy.legacy`.
- [notification](./notification/EGM.md) - Bill and transaction reminder scheduling plus the WorkManager workers that fire them.
- [sync](./sync/EGM.md) - PocketBase transfer sync: periodic `TransferSyncWorker` and the scheduler that arms or cancels it.
- [ui](./ui/EGM.md) - Legacy UI helpers with no design-system dependency; currently `SearchInput`.
- [utils](./utils/EGM.md) - Legacy utility extensions: amount formatting, date/time helpers, Compose/UI extensions, gestures, activity results.

## Invariants
- `rootActivity()` casts `LocalContext.current` to `AppCompatActivity` and `rootScreen()` casts it to `RootScreen`: both throw `ClassCastException` rather than returning null when the composition is not hosted by an activity, so they are only safe in the app's own root.
- `Constants` still points at the **upstream** Ivy Wallet URLs (`ILIYANGERMANOV/privacy-policies`, `Ivy-Apps/ivy-wallet`, the Google Play listing for `com.ivy.wallet`) - the fork has not rebranded them.
- `LogoutLogic.logout()` must clear every sync-side table, not just the local ones: the maintained test asserts exactly one `deleteAll()` for directory accounts, pending transfers, the transfer outbox, transfer confirmations and pending bills. A new table added to the sync feature has to be added here or it survives logout.
- `IvyWalletCtx` is deprecated but is the type every legacy composable resolves through `ivyWalletCtx()`; the cast in `ivyWalletCtx()` assumes the installed context is `IvyWalletCtx`, so an app that supplies another `IvyContext` breaks every legacy screen at once.
- `CATEGORY_UNSPECIFIED_NAME` is a display string compared by value in places - keep it stable.

## Side Effects
- `logout()` deletes all user data: accounts (including the directory), transfers (pending, outbox, confirmations), pending bills, plus clearing settings, planned payment rules, budgets, loans, loan records, categories, tags, transactions, exchange rates, the DataStore file and the user record.
- `logout()` also navigates (injected `Navigation`) and notifies the `DataObserver`.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

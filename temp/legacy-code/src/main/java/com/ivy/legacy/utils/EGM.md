# Legacy Utilities

## Overview
The extension-function toolbox the legacy code leans on: amount formatting and parsing, date/time formatting and conversion, Compose/View/gesture helpers, activity-result plumbing, keyboard and status-bar helpers, validation and wallet display strings. Package `com.ivy.legacy.utils`.

## Interface
- Amount (`AmountFormatting.kt`): `amountToDoubleOrNull`, `amountToDouble`, `normalizeAmount`, `normalizeExpression`, `removeGroupingSeparator`, `normalizeDecimalSeparator`, `localDecimalSeparator`, `localGroupingSeparator`, `Double.format(digits|currencyCode|IvyCurrency?)`, `formatCrypto`, `shortenAmount`, `shouldShortAmount`, `formatInt`, `decimalPartFormatted`, `integerPartFormatted`, `formatInputAmount`, `hasSignificantDecimalPart`, `Long.length`; constants `TRILLION`, `BILLION`, `MILLION`, `N_100K`, `THOUSAND`.
- Date/time (`DateExt.kt`): `timeNowLocal`, `dateNowLocal`, `timeNowUTC`, `timeUTC`, `dateNowUTC`, `startOfDayNowUTC`, `toEpochSeconds`, `millis`, `formatNicely`, `formatNicelyWithTime`, `formatLocalTime`, `formatDateOnly`, `formatDateOnlyWithYear`, `formatDateWeekDayLong`, `formatLocal`, `format`, `convertUTCtoLocal`, `convertUTCto`, `convertLocalToUTC`, `convertUTCToLocal`, `startOfMonth`, `endOfMonth`, `atEndOfDay`, `ivyMinTime`, `ivyMaxTime`, `withDayOfMonthSafe`, `getISOFormattedDateTime`, `closeDay`.
- Compose/UI (`ComposeExt.kt`, `UIExt.kt`): insets, `rememberInteractionSource`, `Modifier.consumeClicks`, `clickableNoIndication`, `drawColoredShadow`, `selectEndTextFieldValue`, density conversions, `openUrl`, keyboard helpers, `setStatusBarDarkTextCompat`, `lerp`/`colorLerp`.
- Gestures (`GesturesExt.kt`): `SwipeListenerState`, `rememberSwipeListenerState`, `Modifier.verticalSwipeListener`, `Modifier.horizontalSwipeListener`.
- Other: `simpleActivityForResultLauncher` (Activity/Fragment), `sendToCrashlytics`/`logToCrashlytics`/`DeveloperException` (`FirebaseExt.kt`), `Fragment.args`/`stringArg` (`MVVMExt.kt`), `numberBetween`/`toUpperCaseLocal`/`capitalizeWords`/`hasLockScreen` (`UtilExt.kt`), `String?.isNotNullOrBlank` (`ValidationExt.kt`), `balancePrefix`/`compactBalancePrefix` (`WalletUtil.kt`), `IvyAnimation.kt`.

## Invariants
- Locale: `localDecimalSeparator()`/`localGroupingSeparator()` read `DecimalFormatSymbols.getInstance()`, i.e. the JVM default locale - parsing and display both depend on ambient locale, and there is no `Locale` parameter to override.
- `Double.format(currency)` picks crypto (up to 9 decimals, trailing zeros trimmed) vs fiat (`#,##0.00`) from `IvyCurrency.isCrypto`; the fiat branch uses `DecimalFormat` with default symbols, so grouping follows the device locale.
- `shortenAmount` uses hardcoded Indonesian suffixes - `rb` (thousand), `jt` (million), `M` (billion), `T` (trillion) - not localised. Changing them changes every abbreviated amount on screen.
- `shouldShortAmount` switches to the shortened form at 100,000 (`N_100K`) and above.
- `hasSignificantDecimalPart` treats a decimal part below `0.009` as absent, so `1.004` renders as `1` in shortened amounts.
- `integerPartFormatted` exists because `Double.toInt()` maps every value in `(-1.0, 0.0)` to `0`; it re-prepends the minus sign for that range.
- `formatInputAmount` rejects input with more decimal places than `decimalCountMax` (default 2) **unless** the currency is crypto - it returns null rather than truncating.
- Every time function in `DateExt.kt` that reads the clock is `@Deprecated("Use the TimeProvider interface via DI")` (or `TimeConverter` for formatting); the two `@Total`-annotated ones (`timeNowUTC`, `dateNowUTC`) are still clock reads.
- `ivyMinTime()`/`ivyMaxTime()` clamp to safe instants - use them instead of `Instant.MIN`/`MAX` when a value round-trips through a database column.

## Side Effects
- `sendToCrashlytics`/`logToCrashlytics` report to Firebase Crashlytics (network).
- `openUrl` hands a URI to the platform (leaves the app).
- `simpleActivityForResultLauncher` registers activity-result contracts.
- `setStatusBarDarkTextCompat`/`hideKeyboard`/`addKeyboardListener` mutate the window/IME state of the hosting activity.
- `hasLockScreen` queries the device keyguard state.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`

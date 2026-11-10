package com.ivy.legacy.android.power

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Whether this device can be trusted to run the background transfer sync.
 *
 * ## Why the app has to know this at all
 *
 * WorkManager will happily tell you it has enqueued the periodic worker, and be
 * technically correct, while the OS never runs it. Measured on the POCOPHONE F1
 * (MIUI, Android 10) on 2026-11-10:
 *
 *     Job #u0a680/55 astesias.pocket.debug
 *       28 minutes past due, standby bucket RARE, not in the device-idle whitelist
 *       Unsatisfied constraints: CONNECTIVITY  (while the wifi was VALIDATED)
 *       implicit: readyNotRestrictedInBg: false
 *     no transfer notification posted for over five hours
 *
 * Nothing in the app was broken and nothing said anything. Transfers simply
 * arrived when someone opened the app, which is the exact complaint (C1) that
 * starts this whole thread: "the notification never comes".
 *
 * The app cannot run that work on the user's behalf. It can, however, ask to be
 * exempted once, and say plainly when it is not.
 */
enum class BatteryOptimisationState {
    /**
     * Below API 23 there is no Doze and no app standby, so nothing to ask for.
     * Unreachable in practice (minSdk is 28) but kept so the rule is total rather
     * than "true for the cases we happen to ship today".
     */
    NotRequired,

    /** The OS has been told to leave this app's background work alone. */
    Exempt,

    /**
     * The OS may defer the sync indefinitely. Not an error and not visible
     * anywhere else in the app - the queue still looks healthy, it just never
     * runs - so this is the state worth surfacing.
     */
    Restricted,
}

/** The decision, isolated from Android so it can be tested on the JVM. */
fun batteryOptimisationState(
    sdkInt: Int,
    isIgnoringOptimisations: Boolean,
): BatteryOptimisationState = when {
    // 23 == Build.VERSION_CODES.M. Written as a literal on purpose: this runs in
    // a plain JVM unit test, where the android.jar stub returns defaults.
    sdkInt < 23 -> BatteryOptimisationState.NotRequired
    isIgnoringOptimisations -> BatteryOptimisationState.Exempt
    else -> BatteryOptimisationState.Restricted
}

/** The same decision, read from the running device. */
fun batteryOptimisationState(context: Context): BatteryOptimisationState {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        ?: return BatteryOptimisationState.NotRequired
    val ignoring = runCatching {
        power.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
    return batteryOptimisationState(Build.VERSION.SDK_INT, ignoring)
}

/**
 * The system dialog that asks for the exemption.
 *
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the manifest and takes the package
 * as its data. If a device answers it with nothing, fall back to the battery
 * optimiser's list so the user is never sent into a dead end.
 */
fun batteryExemptionIntent(context: Context): Intent {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    val resolvable = context.packageManager.resolveActivity(direct, 0) != null
    return if (resolvable) direct else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

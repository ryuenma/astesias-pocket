package com.ivy.legacy.utils

import timber.log.Timber

// Fork change: Ivy Wallet shipped these to Firebase Crashlytics, which reported to
// Ivy's own "ivy-widget" project. Astesia's Pocket is a private, sideloaded build, so
// crash data must not leave the device. These now log locally via Timber, which is
// already wired up in the debug build and readable over `adb logcat`.
//
// Signatures are unchanged so call sites do not need editing. If remote crash
// reporting is ever wanted, point it at a self-hosted sink instead of Ivy's.

fun sendToCrashlytics(
    msg: String
) {
    DeveloperException(msg).sendToCrashlytics(msg)
}

fun Exception.sendToCrashlytics(
    clarification: String? = null
) {
    clarification?.let {
        logToCrashlytics("Log: $it")
    }
    Timber.e(this, "Recorded exception (local only)")
}

fun logToCrashlytics(msg: String) {
    Timber.i("Crashlytics log: %s", msg)
}

class DeveloperException(msg: String) : Exception(msg)
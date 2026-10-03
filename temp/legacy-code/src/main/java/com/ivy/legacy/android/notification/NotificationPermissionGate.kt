package com.ivy.wallet.android.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ivy.base.legacy.SharedPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the app knows about POST_NOTIFICATIONS right now.
 *
 * [NotRequired] exists because the permission only exists from API 33. Below
 * that, notifications are granted at install and a user who wants them off has
 * already gone to system settings - the app has no say either way.
 */
enum class NotificationPermissionState {
    /** Below API 33: no runtime permission exists. */
    NotRequired,

    /** Granted - notifications will be delivered. */
    Granted,

    /** Denied, but the system dialog will still appear if we ask again. */
    DeniedButAskable,

    /**
     * Denied for good: the system will no longer show a dialog, so the only
     * route left is this app's entry in system settings.
     */
    PermanentlyDenied,
}

/**
 * Reads and records the POST_NOTIFICATIONS grant.
 *
 * ## Why the "have we asked" flag is persisted here
 *
 * `shouldShowRequestPermissionRationale()` returns false in two OPPOSITE
 * situations: the permission has never been requested, and the user has denied
 * it permanently. It is a one-bit answer to a three-state question, so it
 * cannot answer "should we ask, or should we send them to settings?" on its
 * own. [SharedPrefs.NOTIFICATIONS_PERMISSION_ASKED] is the missing bit: it is
 * written the moment we launch the request, which makes the permanent-denial
 * case distinguishable from the never-asked case.
 *
 * Reading the rationale flag as "permanently denied" is the classic Android
 * permission bug - it suppresses the request on the first launch of every fresh
 * install, so the permission is never asked for at all.
 *
 * Every read is a live check rather than a cached answer. Auto-reset revokes
 * permissions for apps the user has not opened in months, and the user can flip
 * the switch in settings while the app is backgrounded; a value cached for the
 * session is wrong in both cases, silently, for exactly the least-engaged users.
 */
@Singleton
class NotificationPermissionGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefs: SharedPrefs,
) {

    private fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * True when the runtime permission exists on this OS version.
     *
     * Every entry point into a runtime request must sit behind this, or the app
     * asks for a permission that does not exist and the request silently does
     * nothing.
     */
    fun requiresRuntimeRequest(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun hasAskedBefore(): Boolean =
        sharedPrefs.getBoolean(SharedPrefs.NOTIFICATIONS_PERMISSION_ASKED, false)

    /**
     * Records that we are about to show the system dialog.
     *
     * Written BEFORE the launch, not after the answer: if the process dies while
     * the dialog is up, a flag written afterwards would leave the app believing
     * it had never asked, and the one-bit rationale flag could not correct it.
     */
    fun markAsked() {
        sharedPrefs.putBoolean(SharedPrefs.NOTIFICATIONS_PERMISSION_ASKED, true)
    }

    /**
     * The current state, resolved from the live grant plus the persisted
     * "have we asked" flag.
     *
     * @param shouldShowRationale the result of
     *   `shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)` from the
     *   calling Activity. Passed in rather than read here because the API needs
     *   an Activity, and this class is deliberately free of one.
     */
    fun state(shouldShowRationale: Boolean): NotificationPermissionState =
        resolveState(
            requiresRuntimeRequest = requiresRuntimeRequest(),
            isGranted = isGranted(),
            shouldShowRationale = shouldShowRationale,
            hasAskedBefore = hasAskedBefore(),
        )

    /**
     * Whether notifications can actually be posted right now.
     *
     * Below API 33 this is always true: there is no runtime permission to hold
     * back a notification.
     */
    fun canPostNotifications(): Boolean =
        !requiresRuntimeRequest() || isGranted()

    /**
     * Whether the user has to go to system settings to get reminders back.
     *
     * Only true once the dialog has been denied for good, so this is what the
     * settings row keys its "open settings" action off - it must never fire on
     * a first-time ask, or the app sends a brand-new user to a settings page
     * before they have been asked anything.
     */
    fun needsSystemSettings(shouldShowRationale: Boolean): Boolean =
        state(shouldShowRationale) == NotificationPermissionState.PermanentlyDenied

    /**
     * The app's own notification settings page.
     *
     * `ACTION_APP_NOTIFICATION_SETTINGS` targets notifications specifically,
     * whereas `ACTION_APPLICATION_DETAILS_SETTINGS` dumps the user on the app's
     * general page where the toggle they need is one of several rows.
     */
    fun appNotificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /**
     * The app's details page, for OS versions that lack the notification-settings
     * intent. `ACTION_APPLICATION_DETAILS_SETTINGS` has been valid since API 9,
     * so this is a floor rather than a real expectation of failure.
     */
    fun appDetailsSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    companion object {
        /**
         * The permission decision, as a pure function of four facts.
         *
         * Split out from the class so the three-state machine can be tested
         * without an Android device: [Activity.shouldShowRequestPermissionRationale]
         * and [ContextCompat.checkSelfPermission] are both unmockable in a plain
         * JVM test, and this is exactly the logic that is easy to get wrong.
         *
         * The subtlety worth stating: the rationale flag alone cannot tell
         * "never asked" from "denied for good" - it is false in both. Only
         * [hasAskedBefore] separates them, and reading the rationale flag as
         * "permanently denied" on its own is what makes an app skip the very
         * first request on every fresh install.
         */
        @Suppress("ReturnCount")
        internal fun resolveState(
            requiresRuntimeRequest: Boolean,
            isGranted: Boolean,
            shouldShowRationale: Boolean,
            hasAskedBefore: Boolean,
        ): NotificationPermissionState {
            if (!requiresRuntimeRequest) return NotificationPermissionState.NotRequired
            if (isGranted) return NotificationPermissionState.Granted

            return when {
                // The system still shows a rationale, so a second dialog is
                // possible: the user denied but has not chosen "don't ask again".
                shouldShowRationale -> NotificationPermissionState.DeniedButAskable

                // No rationale, and we know we asked before: Android will not show
                // another dialog, so the only route left is system settings.
                hasAskedBefore -> NotificationPermissionState.PermanentlyDenied

                // No rationale, and we never asked: this is a fresh install.
                // Treating it as a permanent denial is the bug this branch avoids.
                else -> NotificationPermissionState.DeniedButAskable
            }
        }
    }
}

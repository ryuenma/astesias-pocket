package com.ivy.wallet.android.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ivy.base.legacy.SharedPrefs
import com.ivy.legacy.utils.sendToCrashlytics
import com.ivy.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Outcome of trying to post a notification.
 *
 * Exists because the previous version returned Unit and swallowed every
 * exception, so a caller had no way to learn its notification had been dropped.
 * [BlockedByPermission] is the interesting one: on Android 13+ it is the normal
 * result rather than a fault, once the user has declined.
 */
enum class NotificationResult {
    /** The system accepted the notification. */
    Posted,

    /**
     * The user has not granted POST_NOTIFICATIONS, so the notification was never
     * handed to the system. Nothing is shown and nothing throws.
     */
    BlockedByPermission,

    /** No NotificationManager, or the post threw for some other reason. */
    Failed,
}

class NotificationService @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val sharedPrefs: SharedPrefs,
    private val permissionGate: NotificationPermissionGate,
) {

    fun defaultIvyNotification(
        channel: IvyNotificationChannel,
        autoCancel: Boolean = true,
        priority: Int = NotificationCompat.PRIORITY_HIGH
    ): IvyNotification {
        val ivyNotification = IvyNotification(context, channel)
        val color = ContextCompat.getColor(context, R.color.green)
        ivyNotification.setSmallIcon(R.drawable.ic_notification)
            .setColor(color)
            .setPriority(priority)
            .setColorized(true)
            .setLights(color, 1000, 200)
            .setAutoCancel(autoCancel)
        return ivyNotification
    }

    @Suppress("ReturnCount")
    fun showNotification(
        notification: NotificationCompat.Builder,
        notificationId: Int
    ): NotificationResult {
        // The check that matters. On API 33+ an ungranted POST_NOTIFICATIONS does
        // NOT throw: notify() is accepted and the notification is discarded, which
        // is why this used to fail in silence for every user on Android 13.
        if (!permissionGate.canPostNotifications()) {
            // Recorded, not just logged: the worker that just lost this
            // notification runs in the background with no UI, so its log line
            // reaches nobody. The settings screen reads this to tell the user
            // reminders are blocked, and offers a way to unblock them.
            sharedPrefs.putBoolean(SharedPrefs.NOTIFICATIONS_BLOCKED, true)
            Timber.w(
                "Notification %d dropped: POST_NOTIFICATIONS is not granted. " +
                    "Reminders stay silent until the user enables them.",
                notificationId
            )
            return NotificationResult.BlockedByPermission
        }

        // The user turned notifications back on: clear the stale blocked flag so
        // settings stops advertising a problem that no longer exists.
        sharedPrefs.putBoolean(SharedPrefs.NOTIFICATIONS_BLOCKED, false)

        return try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return NotificationResult.Failed
            // Register the channel with the system
            val channel = (notification as IvyNotification).ivyChannel.create(context)

            notificationManager.createNotificationChannel(channel)
            notificationManager.notify(notificationId, notification.build())
            NotificationResult.Posted
        } catch (e: Exception) {
            // Still not swallowed: reported to the local log sink and returned to
            // the caller, so a failed post is a fact someone can act on rather
            // than a return-and-forget.
            e.printStackTrace()
            e.sendToCrashlytics("Cannot show notification $notificationId.")
            NotificationResult.Failed
        }
    }

    /**
     * Whether a notification has recently been dropped for lack of permission.
     *
     * Read by the settings screen to render the "reminders are off" row.
     */
    fun wasBlockedByPermission(): Boolean =
        sharedPrefs.getBoolean(SharedPrefs.NOTIFICATIONS_BLOCKED, false)

    fun dismissNotification(notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}

package com.ivy.legacy.notification

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ivy.domain.AppStarter
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.IvyNotificationId
import com.ivy.wallet.android.notification.NotificationResult
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the owner that money is waiting to be accepted.
 *
 * Before this existed, an incoming transfer was invisible until the recipient
 * opened the app and went looking. Nothing was broken - there was simply no code
 * that notified anyone - so a sender watching their own ledger stayed wrong, and
 * the recipient had no reason to look. This is the missing half of the transfer
 * feature rather than a fix to the confirmation flow.
 *
 * One notification, not one per transfer: the body counts them, and re-notifying
 * updates the same slot instead of stacking copies. See
 * [IvyNotificationId.INCOMING_TRANSFER].
 */
@Singleton
class TransferNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
) {

    fun notifyIncoming(count: Int) {
        if (count <= 0) {
            // Called with nothing to say. Posting would produce an empty
            // notification, which reads as a bug rather than as news.
            return
        }

        val body = context.resources.getQuantityString(
            R.plurals.incoming_transfer_body,
            count,
            count,
        )

        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.TRANSFER_INCOMING,
                priority = NotificationCompat.PRIORITY_HIGH,
            )
            .setContentTitle(context.getString(R.string.incoming_transfer_title))
            .setContentText(body)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    // Distinct from the reminder ids, so opening one feature's
                    // notification does not cancel another's.
                    IvyNotificationId.INCOMING_TRANSFER,
                    appStarter.getRootIntent(),
                    PendingIntent.FLAG_CANCEL_CURRENT or
                        PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE,
                )
            )

        // Checked, not ignored: on API 33+ a declined POST_NOTIFICATIONS does not
        // throw, and a silent no-op is indistinguishable from "no transfer came in".
        when (notificationService.showNotification(notification, IvyNotificationId.INCOMING_TRANSFER)) {
            NotificationResult.Posted -> Timber.i(
                "Notified about $count incoming transfer(s); the inbox holds them"
            )

            NotificationResult.BlockedByPermission -> Timber.i(
                "Incoming transfer notification suppressed: notifications are not " +
                    "allowed. The transfer is still in the inbox."
            )

            NotificationResult.Failed -> Timber.w(
                "Could not post the incoming transfer notification. The transfer is " +
                    "still in the inbox."
            )
        }
    }
}

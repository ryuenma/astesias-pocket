package com.ivy.wallet.android.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.ivy.ui.R

enum class IvyNotificationChannel(
    val channelId: String,
    val channelName: String,
    val description: String,
    val importance: Int = NotificationManager.IMPORTANCE_MAX,
    val bypassDnd: Boolean = true
) {
    TRANSACTION_REMINDER(
        channelId = "transaction_reminder",
        channelName = "Transaction reminder",
        description = "Reminding you to record your transactions on a daily basis.",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    ),

    /**
     * A monthly bill is due.
     *
     * Its own channel rather than a second notification on `TRANSACTION_REMINDER`,
     * because the two are different kinds of thing: that one is "you have not
     * recorded anything today", this one is "this bill is due and unpaid". Folding
     * them together would mean the user could silence the nag he does not care
     * about and lose the bill reminders he does, or vice versa - the system
     * applies importance and sound per channel, so one switch would govern both.
     *
     * High importance but not MAX: a due bill deserves to be seen promptly, but it
     * is a prompt rather than an alarm, and it does not need to be able to
     * override a Do Not Disturb the owner set for the night.
     */
    BILL_REMINDER(
        channelId = "bill_reminder",
        channelName = "Bill reminder",
        description = "Reminding you that a monthly bill is due.",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    );

    @SuppressLint("WrongConstant")
    fun create(context: Context): NotificationChannel {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val colorPurple = ContextCompat.getColor(context, R.color.green)
        val channel = NotificationChannel(
            channelId,
            channelName,
            importance
        )
        channel.description = description
        channel.lightColor = colorPurple
        channel.enableLights(true)
        channel.enableVibration(true)
        channel.setBypassDnd(false)
        return channel
    }
}

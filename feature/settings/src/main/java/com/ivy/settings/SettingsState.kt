package com.ivy.settings

import com.ivy.base.legacy.Theme

@Suppress("DataClassDefaultValues")
data class SettingsState(
    val currencyCode: String,
    val name: String,
    val currentTheme: Theme,
    val lockApp: Boolean,
    val showNotifications: Boolean,
    val hideCurrentBalance: Boolean,
    val hideIncome: Boolean,
    val treatTransfersAsIncomeExpense: Boolean,
    val startDateOfMonth: String,
    val progressState: Boolean,
    val languageOptionVisible: Boolean,
    val syncBaseUrl: String = "",
    val syncProfile: String? = null,
    /**
     * True when the user has switched notifications on but Android is still
     * refusing to deliver them.
     *
     * Kept separate from [showNotifications] on purpose: the pref and the OS
     * grant are two different facts, and on API 33+ they disagree for as long as
     * the user declines. The switch stays ON - the user asked for it - and this
     * flag is what the row uses to admit that nothing is arriving, instead of a
     * switch that claims otherwise.
     */
    val notificationsBlocked: Boolean = false,
    /**
     * The system dialog is on screen right now.
     *
     * The row reads this to keep the user from firing a second request while the
     * first is open: the system would answer the second with an instant denial
     * and make the permission look permanently refused when it is not.
     */
    val requestingNotificationPermission: Boolean = false,
    /**
     * The user has declined for good, so the system dialog will not appear again.
     *
     * The only remaining route is the app's settings page. Derived from the
     * persisted "have we asked" flag, not from the rationale bit alone - see
     * NotificationPermissionGate.
     */
    val notificationsPermanentlyDenied: Boolean = false,
)

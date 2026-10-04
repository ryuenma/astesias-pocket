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
     * The user has declined for good, so the system dialog will not appear again.
     *
     * The only remaining route is the app's settings page. Derived from the
     * persisted "have we asked" flag, not from the rationale bit alone - see
     * NotificationPermissionGate.
     */
    val notificationsPermanentlyDenied: Boolean = false,
    /**
     * How many incoming transfers are waiting for her to accept or decline.
     *
     * Surfaced on the Settings row rather than only inside the inbox, because a
     * count nobody can see is the same defect as an inbox nobody can open: she
     * has no way to learn that money is waiting. Zero hides nothing - the row
     * still exists, so the destination is discoverable before it is ever needed.
     */
    val pendingTransfersCount: Int = 0,
)

package com.ivy.settings

import com.ivy.domain.RootScreen

sealed interface SettingsEvent {
    data class SetCurrency(val newCurrency: String) : SettingsEvent
    data class SetName(val newName: String) : SettingsEvent
    data class ExportToCsv(val rootScreen: RootScreen) : SettingsEvent
    data class ExportToXlsx(val rootScreen: RootScreen) : SettingsEvent
    data class BackupData(val rootScreen: RootScreen) : SettingsEvent
    data object SwitchTheme : SettingsEvent
    data class SetLockApp(val lockApp: Boolean) : SettingsEvent
    data class SetShowNotifications(val showNotifications: Boolean) : SettingsEvent
    data class SetHideCurrentBalance(val hideCurrentBalance: Boolean) : SettingsEvent
    data class SetHideIncome(val hideIncome: Boolean) : SettingsEvent
    data class SetTransfersAsIncomeExpense(val treatTransfersAsIncomeExpense: Boolean) :
        SettingsEvent

    data class SetStartDateOfMonth(val startDate: Int) : SettingsEvent
    data object DeleteCloudUserData : SettingsEvent
    data object DeleteAllUserData : SettingsEvent
    data object SwitchLanguage : SettingsEvent

    /**
     * The user answered the POST_NOTIFICATIONS dialog.
     *
     * Reported back into the ViewModel rather than acted on in the composable,
     * so the outcome is applied to the pref, the WorkManager schedule and the
     * blocked flag in one place instead of being split across two layers that
     * can disagree.
     */
    data class NotificationPermissionResult(val granted: Boolean) : SettingsEvent
}

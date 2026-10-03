package com.ivy.base.legacy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@Deprecated("Use IvyWalletDataStore instead.")
class SharedPrefs @Inject constructor(
    @ApplicationContext
    appContext: Context
) {
    companion object {
        private const val PREFS_FILENAME = "ivy_wallet_prefs"

        const val ONBOARDING_COMPLETED = "onboarding_completed"

        // -------------------------------------- UX ------------------------------------------------
        const val LAST_SELECTED_ACCOUNT_ID = "last_selected_account_id"
        // -------------------------------------- UX ------------------------------------------------

        // ----------------------------- App Settings -----------------------------------------------
        const val APP_LOCK_ENABLED = "lock_app"
        const val START_DATE_OF_MONTH = "start_date_of_month"
        const val SHOW_NOTIFICATIONS = "show_notifications"

        /**
         * Whether we have ever shown the POST_NOTIFICATIONS system dialog.
         *
         * Not cosmetic. `shouldShowRequestPermissionRationale()` is false both
         * before the first ask and after a permanent denial, so this is the only
         * thing that tells those two states apart. See NotificationPermissionGate.
         */
        const val NOTIFICATIONS_PERMISSION_ASKED = "notifications_permission_asked"

        /**
         * Set when a notification was dropped for lack of POST_NOTIFICATIONS.
         *
         * The reminder worker runs in the background where it cannot show
         * anything, so a denial has to be written down for the settings screen
         * to surface later. See NotificationService.showNotification.
         */
        const val NOTIFICATIONS_BLOCKED = "notifications_blocked"
        const val HIDE_CURRENT_BALANCE = "hide_current_balance"
        const val HIDE_INCOME = "hide_income"
        const val TRANSFERS_AS_INCOME_EXPENSE = "transfers_as_inc_exp"
        // ----------------------------- App Settings -----------------------------------------------

        // -------------------------------- Customer Journey ----------------------------------------
        const val _CARD_DISMISSED = "_cj_dismissed"
        // -------------------------------- Customer Journey ----------------------------------------

        // ----------------------------- Others -----------------------------------------------
        const val CATEGORY_SORT_ORDER = "categorySortOrder"
        const val DATA_BACKUP_COMPLETED = "data_backup_completed"
    }

    private val preferences = appContext.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    fun has(key: String): Boolean {
        return preferences.contains(key)
    }

    val all: Map<String, *>
        get() = preferences.all

    fun putInt(key: String, value: Int) {
        val editor = preferences.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun putFloat(key: String, value: Float) {
        val editor = preferences.edit()
        editor.putFloat(key, value)
        editor.apply()
    }

    fun putDouble(key: String, value: Double) {
        val editor = preferences.edit()
        editor.putFloat(key, value.toFloat())
        editor.apply()
    }

    fun putLong(key: String, value: Long) {
        val editor = preferences.edit()
        editor.putLong(key, value)
        editor.apply()
    }

    fun putString(key: String, value: String?) {
        val editor = preferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun putBoolean(key: String, value: Boolean) {
        val editor = preferences.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getLong(key: String, defValue: Long): Long {
        return preferences.getLong(key, defValue)
    }

    fun getInt(key: String, defValue: Int): Int {
        return preferences.getInt(key, defValue)
    }

    fun getFloat(key: String, defValue: Float): Float {
        return preferences.getFloat(key, defValue)
    }

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        return preferences.getBoolean(key, defValue)
    }

    fun getString(key: String): String {
        return preferences.getString(key, null)
            ?: throw IllegalStateException("SharePrefs key '$key' cannot be null")
    }

    fun getString(key: String, defValue: String?): String? {
        return preferences.getString(key, defValue)
    }

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    fun removeAll() {
        preferences.edit().clear().apply()
    }
}

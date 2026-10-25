package com.ivy.wallet

import android.content.Intent
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Theme
import com.ivy.base.legacy.stringRes
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.repository.LegalRepository
import com.ivy.frp.test.TestIdlingResource
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.utils.ioThread
import com.ivy.legacy.utils.readOnly
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.MainScreen
import com.ivy.navigation.Navigation
import com.ivy.navigation.OnboardingScreen
import com.ivy.ui.R
import com.ivy.legacy.notification.BillReminderScheduler
import com.ivy.legacy.sync.TransferSyncScheduler
import com.ivy.wallet.domain.deprecated.logic.notification.TransactionReminderLogic
import com.ivy.wallet.migrations.MigrationsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class RootViewModel @Inject constructor(
    private val ivyContext: IvyWalletCtx,
    private val nav: Navigation,
    private val settingsDao: SettingsDao,
    private val sharedPrefs: SharedPrefs,
    private val transactionReminderLogic: TransactionReminderLogic,
    private val billReminderScheduler: BillReminderScheduler,
    private val transferSyncScheduler: TransferSyncScheduler,
    private val migrationsManager: MigrationsManager,
    private val legalRepo: LegalRepository,
) : ViewModel() {

    companion object {
        const val EXTRA_ADD_TRANSACTION_TYPE = "add_transaction_type_extra"

        const val USER_INACTIVITY_TIME_LIMIT = 60 // Time in seconds
    }

    private var appLockEnabled = false

    private val _appLocked = MutableStateFlow<Boolean?>(null)
    val appLocked = _appLocked.readOnly()

    fun start(systemDarkMode: Boolean, intent: Intent) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                val theme = settingsDao.findAll().firstOrNull()?.theme
                    ?: if (systemDarkMode) Theme.DARK else Theme.LIGHT
                ivyContext.switchTheme(theme)

                ivyContext.initStartDayOfMonthInMemory(sharedPrefs = sharedPrefs)
            }

            TestIdlingResource.decrement()
        }

        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                appLockEnabled = sharedPrefs.getBoolean(SharedPrefs.APP_LOCK_ENABLED, false)
                // initial app locked state
                _appLocked.value = appLockEnabled

                if (isOnboardingCompleted()) {
                    navigateOnboardedUser(intent)
                } else {
                    nav.navigateTo(OnboardingScreen)
                }
                // Legal acceptance is re-homed during the onboarding rework; the
                // disclaimer screen belonged to a removed upstream page module.
            }

            TestIdlingResource.decrement()
        }

        viewModelScope.launch {
            migrationsManager.executeMigrations()
        }
    }

    private fun navigateOnboardedUser(intent: Intent) {
        if (!handleSpecialStart(intent)) {
            nav.navigateTo(MainScreen)
            transactionReminderLogic.scheduleReminder()
            scheduleBillReminders()
        }
    }

    /**
     * Re-arms every bill reminder on each app start.
     *
     * Deliberately unconditional and idempotent rather than gated on the
     * notifications pref. Two reasons:
     *
     *  - It is re-armed on every start, so it is the repair path. A request lost to
     *    a reboot, an app update or a "clear all data" in system settings is
     *    re-created here rather than needing its own recovery code. Each rule is
     *    enqueued as unique work with REPLACE, so re-arming cannot stack duplicates.
     *  - The pending bill is written whether or not the notification can be posted.
     *    Tying the schedule to the pref would mean that with notifications off, no
     *    pending bill ever appears either - and the bill is the part the owner
     *    needs. Suppression belongs at the moment of posting, which is where
     *    `NotificationService` already enforces it.
     */
    private fun scheduleBillReminders() {
        viewModelScope.launch {
            // Deferred to IO: this reads the rules table and enqueues into
            // WorkManager, which touches its own database.
            ioThread {
                billReminderScheduler.scheduleAll()
                transferSyncScheduler.scheduleIfConfigured()
            }
        }
    }

    @Suppress("SwallowedException")
    private fun handleSpecialStart(intent: Intent): Boolean {
        val addTrnType: TransactionType? = try {
            intent.getSerializableExtra(EXTRA_ADD_TRANSACTION_TYPE) as? TransactionType
                ?: TransactionType.valueOf(intent.getStringExtra(EXTRA_ADD_TRANSACTION_TYPE) ?: "")
        } catch (e: IllegalArgumentException) {
            null
        }

        if (addTrnType != null) {
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = addTrnType
                )
            )

            return true
        }

        return false
    }

    @Suppress("EmptyFunctionBlock")
    fun handleBiometricAuthResult(
        onAuthSuccess: () -> Unit = {}
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Timber.d(stringRes(R.string.authentication_succeeded))
                unlockApp()
                onAuthSuccess()
            }

            override fun onAuthenticationFailed() {
                Timber.d(stringRes(R.string.authentication_failed))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            }
        }
    }

    private fun isOnboardingCompleted(): Boolean {
        return sharedPrefs.getBoolean(SharedPrefs.ONBOARDING_COMPLETED, false)
    }

    // App Lock & UserInactivity --------------------------------------------------------------------
    fun isAppLockEnabled(): Boolean {
        return appLockEnabled
    }

    fun isAppLocked(): Boolean {
        // by default we assume that the app is locked
        return appLocked.value ?: true
    }

    fun lockApp() {
        _appLocked.value = true
    }

    fun unlockApp() {
        _appLocked.value = false
    }

    private val userInactiveTime = AtomicLong(0)
    private var userInactiveJob: Job? = null

    @Suppress("MagicNumber")
    fun startUserInactiveTimeCounter() {
        if (userInactiveJob?.isActive == true) return

        userInactiveJob = viewModelScope.launch(Dispatchers.IO) {
            while (userInactiveTime.get() < USER_INACTIVITY_TIME_LIMIT && isActive) {
                delay(1000)
                userInactiveTime.incrementAndGet()
            }

            if (!isAppLocked()) {
                lockApp()
            }

            cancel()
        }
    }

    fun checkUserInactiveTimeStatus() {
        if (userInactiveTime.get() < USER_INACTIVITY_TIME_LIMIT) {
            if (userInactiveJob != null && userInactiveJob?.isCancelled == false) {
                userInactiveJob?.cancel()
                resetUserInactiveTimer()
            }
        }
    }

    fun resetUserInactiveTimer() {
        userInactiveTime.set(0)
    }
}

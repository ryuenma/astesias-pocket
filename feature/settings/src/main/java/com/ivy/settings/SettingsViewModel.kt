package com.ivy.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.sync.DirectoryAuthApi
import com.ivy.data.sync.DirectorySyncRepository
import com.ivy.data.sync.SyncConfig
import com.ivy.base.legacy.Theme
import com.ivy.base.legacy.refreshWidget
import com.ivy.data.backup.BackupDataUseCase
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.write.WriteSettingsDao
import com.ivy.data.model.primitive.AssetCode
import com.ivy.domain.RootScreen
import com.ivy.domain.usecase.csv.ExportCsvUseCase
import com.ivy.domain.usecase.xlsx.ExportXlsxUseCase
import com.ivy.domain.usecase.exchange.SyncExchangeRatesUseCase
import com.ivy.domain.usecase.transfer.PendingTransferRepository
import com.ivy.domain.usecase.transfer.TransferConfirmationRepository
import com.ivy.frp.monad.Res
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.LogoutLogic
import com.ivy.legacy.domain.action.settings.UpdateSettingsAct
import com.ivy.legacy.utils.getISOFormattedDateTime
import com.ivy.legacy.utils.ioThread
import com.ivy.legacy.utils.timeNowUTC
import com.ivy.legacy.utils.uiThread
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.android.notification.NotificationPermissionGate
import com.ivy.wallet.android.notification.NotificationPermissionState
import com.ivy.wallet.domain.action.global.StartDayOfMonthAct
import com.ivy.wallet.domain.action.global.UpdateStartDayOfMonthAct
import com.ivy.wallet.domain.action.settings.SettingsAct
import com.ivy.wallet.domain.deprecated.logic.notification.TransactionReminderLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@Stable
@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val ivyContext: IvyWalletCtx,
    private val logoutLogic: LogoutLogic,
    private val sharedPrefs: SharedPrefs,
    private val syncConfig: SyncConfig,
    private val directorySync: DirectorySyncRepository,
    private val pendingTransferRepository: PendingTransferRepository,
    private val transferConfirmationRepository: TransferConfirmationRepository,
    private val backupDataUseCase: BackupDataUseCase,
    private val startDayOfMonthAct: StartDayOfMonthAct,
    private val updateStartDayOfMonthAct: UpdateStartDayOfMonthAct,
    private val syncExchangeRatesUseCase: SyncExchangeRatesUseCase,
    private val settingsAct: SettingsAct,
    private val updateSettingsAct: UpdateSettingsAct,
    private val settingsWriter: WriteSettingsDao,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val exportXlsxUseCase: ExportXlsxUseCase,
    private val permissionGate: NotificationPermissionGate,
    private val transactionReminderLogic: TransactionReminderLogic,
    private val dispatchersProvider: DispatchersProvider,
    @ApplicationContext private val context: Context
) : ComposeViewModel<SettingsState, SettingsEvent>() {

    private val currencyCode = mutableStateOf("")
    private val name = mutableStateOf("")
    private val currentTheme = mutableStateOf<Theme>(Theme.AUTO)
    private val lockApp = mutableStateOf(false)
    private val showNotifications = mutableStateOf(true)
    private val hideCurrentBalance = mutableStateOf(false)
    private val hideIncome = mutableStateOf(false)
    private val treatTransfersAsIncomeExpense = mutableStateOf(false)
    private val startDateOfMonth = mutableIntStateOf(1)
    private val progressState = mutableStateOf(false)

    private val notificationsBlocked = mutableStateOf(false)
    private val requestingNotificationPermission = mutableStateOf(false)
    private val notificationsPermanentlyDenied = mutableStateOf(false)

    /** Incoming transfers awaiting her decision. Drives the Settings row badge. */
    private val pendingTransfersCount = mutableIntStateOf(0)

    /**
     * Answers about transfers she sent that she has not read.
     *
     * Durable rather than fetched here, so the badge survives the sync that
     * learned the answer finishing after this screen was built.
     */
    private val unacknowledgedAnswersCount = mutableIntStateOf(0)

    @Composable
    override fun uiState(): SettingsState {
        LaunchedEffect(Unit) {
            onStart()
        }

        return SettingsState(
            currencyCode = getCurrencyCode(),
            name = getName(),
            currentTheme = getCurrentTheme(),
            lockApp = getLockApp(),
            showNotifications = getShowNotifications(),
            hideCurrentBalance = getHideCurrentBalance(),
            treatTransfersAsIncomeExpense = getTreatTransfersAsIncomeExpense(),
            startDateOfMonth = getStartDateOfMonth(),
            progressState = getProgressState(),
            hideIncome = getHideIncome(),
            languageOptionVisible = isLanguageOptionVisible(),
            syncBaseUrl = syncConfig.baseUrl.orEmpty(),
            syncProfile = syncConfig.profileId,
            // Read from the token's own `exp`, never from a network call. A dead
            // token is a fact the device already holds, and asking the server
            // would produce exactly the ambiguous empty answer that let this
            // hide: a read with one returns HTTP 200 and totalItems 0.
            syncTokenExpired = syncConfig.profileId != null && !syncConfig.isTokenUsable,
            notificationsBlocked = getNotificationsBlocked(),
            notificationsPermanentlyDenied = getNotificationsPermanentlyDenied(),
            pendingTransfersCount = pendingTransfersCount.value,
            unacknowledgedAnswersCount = unacknowledgedAnswersCount.value,
        )
    }

    private suspend fun onStart() {
        initializeCurrency()
        initializeName()
        initializeCurrentTheme()
        initializeLockApp()
        initializeShowNotifications()
        initializeHideCurrentBalance()
        initializeHideIncome()
        initializeTransfersAsIncomeExpense()
        initializeStartDateOfMonth()
        refreshPendingTransfersCount()
        refreshUnacknowledgedAnswersCount()
    }

    /**
     * Counts what is waiting in the inbox.
     *
     * Read fresh on every entry to Settings rather than cached, because the count
     * exists to tell her money has arrived: a stale zero is worse than no row at
     * all. Failure is swallowed for the same reason the rest of the sync reads
     * are - a missing count must not take the settings screen down with it.
     */
    private suspend fun refreshPendingTransfersCount() {
        pendingTransfersCount.intValue = runCatching {
            pendingTransferRepository.pending().size
        }.getOrElse { error ->
            Timber.e(error, "Could not count pending transfers")
            pendingTransfersCount.intValue
        }
    }

    /**
     * Counts the partner's answers she has not opened.
     *
     * Read fresh on every entry rather than cached, for the same reason the
     * pending count is: this number exists to tell her that money she sent has
     * been answered, and a stale zero is worse than no row at all.
     *
     * The sync itself deliberately does NOT run here. Opening Settings must not
     * depend on a network round-trip, and an answer learned by a background sync
     * will still be counted the next time she comes back - which is the property
     * that makes the persisted answer worth having in the first place.
     */
    private suspend fun refreshUnacknowledgedAnswersCount() {
        unacknowledgedAnswersCount.intValue = runCatching {
            transferConfirmationRepository.unacknowledgedCount()
        }.getOrElse { error ->
            Timber.e(error, "Could not count unacknowledged transfer answers")
            unacknowledgedAnswersCount.intValue
        }
    }

    private suspend fun initializeCurrency() {
        val settings = ioThread {
            settingsDao.findFirst()
        }

        currencyCode.value = settings.currency
    }

    private suspend fun initializeName() {
        val settings = ioThread {
            settingsDao.findFirst()
        }

        name.value = settings.name
    }

    private suspend fun initializeCurrentTheme() {
        currentTheme.value = settingsAct(Unit).theme
    }

    private fun initializeLockApp() {
        lockApp.value = sharedPrefs.getBoolean(SharedPrefs.APP_LOCK_ENABLED, false)
    }

    private fun initializeShowNotifications() {
        showNotifications.value = sharedPrefs.getBoolean(
            SharedPrefs.SHOW_NOTIFICATIONS, true
        )
        // Conservative default until the screen supplies the real rationale bit:
        // a wrongly-reported "permanently denied" only adds a settings shortcut,
        // whereas a wrongly-reported "askable" would show a button that cannot work.
        applyPermissionState(shouldShowRationale = true)
    }

    /**
     * Re-derives the permission flags from the live grant.
     *
     * Called by the screen with the current rationale bit on entry and on every
     * resume, because [Activity.shouldShowRequestPermissionRationale] needs an
     * Activity - which this ViewModel has no business holding - and because the
     * user can flip the permission in system settings while the app sits in the
     * background. Auto-reset can revoke it too, for users who rarely open the
     * app, which is exactly why nothing here is cached for the session.
     */
    fun refreshNotificationPermission(shouldShowRationale: Boolean) {
        applyPermissionState(shouldShowRationale)
    }

    /**
     * Whether the OS will show a permission dialog for notifications at all.
     *
     * Below API 33 there is no runtime permission, so the toggle simply works and
     * this stays false without asking for anything.
     */
    fun requiresNotificationPermissionRequest(): Boolean =
        permissionGate.requiresRuntimeRequest()

    /**
     * Folds the OS grant into the three flags the row renders from.
     *
     * Deliberately does not touch [showNotifications]: the user asked for
     * notifications and the switch should keep saying so. What changes is
     * whether the row admits that nothing is arriving and what it offers next.
     */
    private fun applyPermissionState(shouldShowRationale: Boolean) {
        when (permissionGate.state(shouldShowRationale)) {
            NotificationPermissionState.NotRequired,
            NotificationPermissionState.Granted -> {
                notificationsBlocked.value = false
                notificationsPermanentlyDenied.value = false
                // No longer blocked, so the flag the worker writes can be cleared:
                // leaving it set would show a stale "reminders are off" warning
                // after the user fixed it in system settings.
                sharedPrefs.putBoolean(SharedPrefs.NOTIFICATIONS_BLOCKED, false)
            }

            NotificationPermissionState.DeniedButAskable -> {
                notificationsBlocked.value = showNotifications.value
                notificationsPermanentlyDenied.value = false
            }

            NotificationPermissionState.PermanentlyDenied -> {
                notificationsBlocked.value = showNotifications.value
                notificationsPermanentlyDenied.value = true
            }
        }
    }

    private fun initializeHideCurrentBalance() {
        hideCurrentBalance.value =
            sharedPrefs.getBoolean(SharedPrefs.HIDE_CURRENT_BALANCE, false)
    }

    private fun initializeHideIncome() {
        hideIncome.value =
            sharedPrefs.getBoolean(SharedPrefs.HIDE_INCOME, false)
    }

    private fun initializeTransfersAsIncomeExpense() {
        treatTransfersAsIncomeExpense.value =
            sharedPrefs.getBoolean(SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE, false)
    }

    private suspend fun initializeStartDateOfMonth() {
        startDateOfMonth.intValue = startDayOfMonthAct(Unit)
    }

    @Composable
    private fun getCurrencyCode(): String {
        return currencyCode.value
    }

    @Composable
    private fun getName(): String {
        return name.value
    }

    @Composable
    private fun getCurrentTheme(): Theme {
        return currentTheme.value
    }

    @Composable
    private fun getLockApp(): Boolean {
        return lockApp.value
    }

    @Composable
    private fun getShowNotifications(): Boolean {
        return showNotifications.value
    }

    @Composable
    private fun getHideCurrentBalance(): Boolean {
        return hideCurrentBalance.value
    }

    @Composable
    private fun getHideIncome(): Boolean {
        return hideIncome.value
    }

    @Composable
    private fun getTreatTransfersAsIncomeExpense(): Boolean {
        return treatTransfersAsIncomeExpense.value
    }

    @Composable
    private fun getStartDateOfMonth(): String {
        return startDateOfMonth.intValue.toString()
    }

    @Composable
    private fun getProgressState(): Boolean {
        return progressState.value
    }

    @Composable
    private fun getNotificationsBlocked(): Boolean = notificationsBlocked.value

    @Composable
    private fun getNotificationsPermanentlyDenied(): Boolean =
        notificationsPermanentlyDenied.value

    private fun isLanguageOptionVisible(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Connect this device to the sync server.
     *
     * The password is used once over the wire and never persisted; only the
     * server's token and the resolved profile id are kept.
     *
     * On success this ALSO publishes this device's accounts, because that is
     * the first moment a profileId exists and the server's `createRule` refuses
     * any body whose `profileId` is not this device's own. Without it the other
     * phone's transfer picker stays empty for an owner who connected but never
     * happened to create an account afterwards.
     *
     * Best-effort and off the caller's critical path: the account is already
     * saved locally, so a publish that fails or is slow must not delay or fail
     * a sign-in that actually worked. Failures are logged; [SyncConfig] has
     * already stored the credentials by the time this runs.
     *
     * @return the result, so the modal can name the actual failure rather than
     *   shrugging at "could not connect".
     */
    suspend fun signInToSync(
        baseUrl: String,
        email: String,
        password: String
    ): DirectoryAuthApi.Result {
        val result = syncConfig.signIn(baseUrl, email, password)
        if (result.outcome == DirectoryAuthApi.Outcome.SUCCESS) {
            withContext(NonCancellable + dispatchersProvider.io) {
                runCatching { directorySync.publishLocalAccounts() }
                    .onFailure { Timber.e(it, "Signed in, but publishing accounts failed") }
            }
        }
        return result
    }

    fun disconnectSync() {
        syncConfig.clear()
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetCurrency -> setCurrency(event.newCurrency)
            is SettingsEvent.SetName -> setName(event.newName)
            is SettingsEvent.ExportToCsv -> exportToCSV(event.rootScreen)
            is SettingsEvent.ExportToXlsx -> exportToXlsx(event.rootScreen)
            is SettingsEvent.BackupData -> exportToZip(event.rootScreen)
            SettingsEvent.SwitchTheme -> switchTheme()
            is SettingsEvent.SetLockApp -> setLockApp(event.lockApp)
            is SettingsEvent.SetShowNotifications -> setShowNotifications(event.showNotifications)
            is SettingsEvent.NotificationPermissionResult ->
                onNotificationPermissionResult(event.granted)
            is SettingsEvent.SetHideCurrentBalance -> setHideCurrentBalance(
                event.hideCurrentBalance
            )

            is SettingsEvent.SetHideIncome -> setHideIncome(
                event.hideIncome
            )

            is SettingsEvent.SetTransfersAsIncomeExpense -> setTransfersAsIncomeExpense(
                event.treatTransfersAsIncomeExpense
            )

            is SettingsEvent.SetStartDateOfMonth -> setStartDateOfMonth(event.startDate)

            SettingsEvent.DeleteCloudUserData -> deleteCloudUserData()
            SettingsEvent.DeleteAllUserData -> deleteAllUserData()
            SettingsEvent.SwitchLanguage -> switchLanguage()
        }
    }

    private fun setCurrency(newCurrency: String) {
        currencyCode.value = newCurrency

        viewModelScope.launch {
            ioThread {
                settingsWriter.save(
                    settingsDao.findFirst().copy(
                        currency = newCurrency
                    )
                )
                AssetCode.from(newCurrency).onRight {
                    syncExchangeRatesUseCase.sync(it)
                }
            }
        }
    }

    private fun setName(newName: String) {
        name.value = newName

        viewModelScope.launch {
            ioThread {
                settingsWriter.save(
                    settingsDao.findFirst().copy(
                        name = newName
                    )
                )
            }
        }
    }

    private fun exportToCSV(rootScreen: RootScreen) {
        ivyContext.createNewFile(
            "IvyWalletExport_${
                timeNowUTC().getISOFormattedDateTime()
            }.csv"
        ) { fileUri ->
            viewModelScope.launch {
                exportCsvUseCase.exportToFile(
                    outputFile = fileUri
                )

                rootScreen.shareCSVFile(
                    fileUri = fileUri
                )
            }
        }
    }

    private fun exportToXlsx(rootScreen: RootScreen) {
        ivyContext.createNewFile(
            "IvyWalletExport_${
                timeNowUTC().getISOFormattedDateTime()
            }.xlsx"
        ) { fileUri ->
            viewModelScope.launch {
                exportXlsxUseCase.exportToFile(
                    outputFile = fileUri
                )

                // The MIME type has to travel with the file or spreadsheet apps
                // open it as plain text.
                rootScreen.shareFile(
                    fileUri = fileUri,
                    mimeType = XLSX_MIME_TYPE
                )
            }
        }
    }

    private fun exportToZip(rootScreen: RootScreen) {
        ivyContext.createNewFile(
            "IvyWalletBackup_${
                timeNowUTC().getISOFormattedDateTime()
            }.zip"
        ) { fileUri ->
            viewModelScope.launch(Dispatchers.IO) {
                progressState.value = true
                backupDataUseCase.exportToFile(zipFileUri = fileUri)
                progressState.value = false

                sharedPrefs.putBoolean(SharedPrefs.DATA_BACKUP_COMPLETED, true)
                ivyContext.dataBackupCompleted = true

                uiThread {
                    rootScreen.shareZipFile(
                        fileUri = fileUri
                    )
                }
            }
        }
    }

    private fun switchTheme() {
        viewModelScope.launch {
            settingsAct.getSettingsWithNextTheme().run {
                updateSettingsAct(this)
                ivyContext.switchTheme(this.theme)
                currentTheme.value = this.theme
            }
        }
    }

    private fun setLockApp(lock: Boolean) {
        lockApp.value = lock

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.APP_LOCK_ENABLED, lock)
        }
    }

    /**
     * The notifications toggle.
     *
     * Turning it ON now asks for POST_NOTIFICATIONS, and only here. This is the
     * one moment where the request costs nothing and means something: the user
     * has just told us they want reminders, so the system dialog is answering a
     * question they asked rather than interrupting something they were doing.
     * An automatic prompt anywhere else - on launch, after onboarding, on the
     * first transaction - is denied by reflex and is not retried.
     *
     * Turning it OFF also cancels the scheduled work, which the pref alone never
     * did: the 24-hour worker kept being enqueued and kept waking the device up
     * to read a pref that said "stay quiet".
     */
    private fun setShowNotifications(notificationsShow: Boolean) {
        if (!notificationsShow) {
            showNotifications.value = false
            notificationsBlocked.value = false
            requestingNotificationPermission.value = false

            viewModelScope.launch {
                sharedPrefs.putBoolean(SharedPrefs.SHOW_NOTIFICATIONS, false)
                // Deferred to IO: WorkManager.cancelUniqueWork touches its database.
                ioThread {
                    transactionReminderLogic.cancelReminder()
                }
            }
            return
        }

        showNotifications.value = true

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)
            scheduleReminderIfAllowed()

            // The state write above is deliberately outside this launch, and is
            // what the false branch has always done. Only the false branch used
            // to touch the field at all, so this switch was a one-way latch: it
            // turned off and could never be turned back on until the process was
            // restarted and the pref re-read. The state field and the pref are two
            // records of the same choice and must be written in both directions,
            // in the same order, or the switch lies until the next cold start.

            // Below API 33 the permission does not exist, so nothing is asked for
            // and the switch is simply true. Guarded here rather than in the
            // composable so the rule lives in one place.
            if (permissionGate.requiresRuntimeRequest() &&
                !permissionGate.canPostNotifications()
            ) {
                // Flag set before the launch, not after the answer: if the process
                // dies while the dialog is up, a flag written afterwards would
                // leave the row believing no request had ever been made.
                permissionGate.markAsked()
                requestingNotificationPermission.value = true
                notificationsBlocked.value = true
            } else {
                // Already granted (or not needed): nothing to ask, so the switch is
                // telling the truth as it stands.
                notificationsBlocked.value = false
                requestingNotificationPermission.value = false
            }
        }
    }

    /**
     * Re-arms the daily reminder, but only once reminders can actually arrive.
     *
     * Enqueueing work that is guaranteed to be suppressed buys nothing and costs a
     * daily wakeup, so on API 33+ with no permission the schedule is left empty
     * and the row explains why. Once the user grants the permission,
     * [onNotificationPermissionResult] calls this to fill the gap.
     */
    private suspend fun scheduleReminderIfAllowed() {
        if (!permissionGate.canPostNotifications()) {
            Timber.i("Not scheduling the daily reminder: notifications are not permitted.")
            return
        }
        ioThread {
            transactionReminderLogic.scheduleReminder()
        }
    }

    /**
     * The user answered the POST_NOTIFICATIONS dialog.
     *
     * A denial is not treated as a temporary condition to retry silently. The
     * pref keeps saying the user wants notifications, so the row stays ON and
     * switches to the blocked presentation: what is not arriving, why, and the
     * one action that can change it.
     */
    fun onNotificationPermissionResult(granted: Boolean) {
        requestingNotificationPermission.value = false

        if (granted) {
            notificationsBlocked.value = false
            notificationsPermanentlyDenied.value = false
            sharedPrefs.putBoolean(SharedPrefs.NOTIFICATIONS_BLOCKED, false)

            viewModelScope.launch {
                // The permission may only just have arrived, so the work that was
                // deliberately not scheduled while it was missing is armed now.
                scheduleReminderIfAllowed()
            }
            return
        }

        notificationsBlocked.value = true
        notificationsPermanentlyDenied.value =
            permissionGate.needsSystemSettings(shouldShowRationale = false)
        Timber.i(
            "Notification permission declined; the settings row will offer a way to re-enable it."
        )
    }

    /**
     * Whether a system permission dialog is waiting to be shown.
     *
     * Read by the screen to launch it, from a LaunchedEffect keyed on this flag:
     * keying on the flag is what makes it one request per request rather than one
     * per recomposition.
     */
    @Composable
    fun notificationPermissionRequestPending(): Boolean =
        requestingNotificationPermission.value

    /**
     * Opens this app's notification settings.
     *
     * The only route left once the user has declined for good: the system will
     * not show another dialog, so the app cannot re-request.
     */
    fun openNotificationSettings(context: Context) {
        // A settings Intent cannot carry FLAG_ACTIVITY_NEW_TASK when launched from
        // an Activity context, and the activity here is the one already on top.
        //
        // The fallback is around startActivity, not around building the Intent:
        // an Intent never throws, the launch is what fails when no activity on
        // the device handles the action. minSdk is 28 and
        // ACTION_APP_NOTIFICATION_SETTINGS exists since API 26, so this is a
        // floor for OEM ROMs that drop the screen rather than a real expectation.
        try {
            context.startActivity(permissionGate.appNotificationSettingsIntent())
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No app notification settings screen; opening app details instead.")
            context.startActivity(permissionGate.appDetailsSettingsIntent())
        }
    }

    private fun setHideCurrentBalance(hideBalance: Boolean) {
        hideCurrentBalance.value = hideBalance

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.HIDE_CURRENT_BALANCE, hideBalance)
        }
    }

    private fun setHideIncome(isHideIncome: Boolean) {
        hideIncome.value = isHideIncome

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.HIDE_INCOME, isHideIncome)
        }
    }

    private fun setTransfersAsIncomeExpense(setTransfersAsIncomeExpense: Boolean) {
        treatTransfersAsIncomeExpense.value = setTransfersAsIncomeExpense

        viewModelScope.launch {
            sharedPrefs.putBoolean(
                SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE,
                treatTransfersAsIncomeExpense.value
            )
        }
    }

    private fun setStartDateOfMonth(startDate: Int) {
        viewModelScope.launch {
            when (val res = updateStartDayOfMonthAct(startDate)) {
                is Res.Err -> {}
                is Res.Ok -> {
                    startDateOfMonth.intValue = res.data
                }
            }
        }
    }

    private fun deleteCloudUserData() {
        viewModelScope.launch {
            cloudLogout()
        }
    }

    private fun cloudLogout() {
        viewModelScope.launch {
            logoutLogic.cloudLogout()
        }
    }

    private fun deleteAllUserData() {
        viewModelScope.launch {
            logout()
        }
    }

    private fun logout() {
        viewModelScope.launch {
            logoutLogic.logout()
        }
    }

    private fun switchLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.applicationContext.startActivity(intent)
        }
    }

    private companion object {
        /**
         * Official xlsx MIME type, which is what makes Google Sheets and Excel
         * treat the file as a workbook rather than as plain text.
         */
        const val XLSX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}

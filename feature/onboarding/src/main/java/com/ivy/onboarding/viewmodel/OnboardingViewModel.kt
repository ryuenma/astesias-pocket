package com.ivy.onboarding.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Theme
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.write.WriteSettingsDao
import com.ivy.data.profile.ProfileId
import com.ivy.data.profile.ProfileRegistry
import com.ivy.legacy.datamodel.Settings
import com.ivy.legacy.utils.ioThread
import com.ivy.navigation.Navigation
import com.ivy.navigation.OnboardingScreen
import com.ivy.onboarding.OnboardingDetailState
import com.ivy.onboarding.OnboardingEvent
import com.ivy.onboarding.OnboardingState
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.data.IvyCurrency
import com.ivy.wallet.domain.deprecated.logic.PreloadDataLogic
import com.ivy.wallet.domain.deprecated.logic.notification.TransactionReminderLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Four steps: splash -> import -> currency -> profile name.
 *
 * The account and category editors are gone. Both were reachable only from the
 * removed steps, and an imported backup supplies them anyway.
 */
@Stable
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val nav: Navigation,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val settingsWriter: WriteSettingsDao,
    private val profileRegistry: ProfileRegistry,

    sharedPrefs: SharedPrefs,
    transactionReminderLogic: TransactionReminderLogic,
    preloadDataLogic: PreloadDataLogic,
) : ComposeViewModel<OnboardingDetailState, OnboardingEvent>() {

    private val _state = mutableStateOf(OnboardingState.SPLASH)
    val state: State<OnboardingState> = _state

    private val _currency = mutableStateOf(IvyCurrency.getDefault())

    /**
     * The id this onboarding run will register. Defaults to DEFAULT because a
     * returning user re-running onboarding lands back in the same profile.
     */
    private val _profileId = mutableStateOf(ProfileId.DEFAULT)

    @Composable
    override fun uiState(): OnboardingDetailState {
        return OnboardingDetailState(
            currency = _currency.value,
            profileId = _profileId.value,
            profileName = profileRegistry.activeProfile().name,
        )
    }

    private val router = OnboardingRouter(
        state = _state,
        nav = nav,
        accountDao = accountDao,
        sharedPrefs = sharedPrefs,
        preloadDataLogic = preloadDataLogic,
        transactionReminderLogic = transactionReminderLogic,
        profileRegistry = profileRegistry,
    )

    fun start(screen: OnboardingScreen, isSystemDarkMode: Boolean) {
        viewModelScope.launch {
            initiateSettings(isSystemDarkMode)
            router.initBackHandling(screen = screen, viewModelScope = viewModelScope)
            // Deliberately does NOT advance the state: the splash is a real step the
            // user leaves with "Get started", not a timed splash screen. Calling
            // router.splashNext() here skipped straight past it.
        }
    }

    private suspend fun initiateSettings(isSystemDarkMode: Boolean) {
        val defaultCurrency = IvyCurrency.getDefault()

        ioThread {
            if (settingsDao.findAll().isEmpty()) {
                settingsWriter.save(
                    Settings(
                        theme = if (isSystemDarkMode) Theme.DARK else Theme.LIGHT,
                        name = "",
                        baseCurrency = defaultCurrency.code,
                        bufferAmount = 1000.0.toBigDecimal()
                    ).toEntity()
                )
            }
        }

        // Read back what is actually stored rather than assuming the default.
        // Re-running onboarding over a populated profile used to re-select the
        // device currency and offer to overwrite the real one.
        adoptStoredCurrency(defaultCurrency)
    }

    /**
     * Points the currency step at the currency the settings row already holds.
     *
     * This is what stops an import from being undone: the importer writes the
     * backup's own currency (IDR), and the step must present that, not the
     * device default, or completing onboarding replaces it.
     */
    private suspend fun adoptStoredCurrency(fallback: IvyCurrency) {
        val storedCode = ioThread { settingsDao.findFirstOrNull()?.currency }
        val code = preselectedCurrencyCode(storedCode, fallback.code)
        _currency.value = IvyCurrency.fromCode(code) ?: fallback
    }

    override fun onEvent(event: OnboardingEvent) {
        viewModelScope.launch {
            when (event) {
                OnboardingEvent.SplashNext -> router.splashNext()
                OnboardingEvent.StartImport -> router.startImport()
                OnboardingEvent.ImportSkip -> router.importSkip()
                is OnboardingEvent.ImportFinished -> router.importFinished(event.success)
                is OnboardingEvent.SetBaseCurrency -> setBaseCurrency(event.baseCurrency)
                is OnboardingEvent.SetProfileName -> {
                    _profileId.value = event.id
                    router.setProfileNameNext(
                        id = event.id,
                        rawName = event.name,
                        scope = viewModelScope,
                    )
                }
            }
        }
    }

    private suspend fun setBaseCurrency(baseCurrency: IvyCurrency) {
        ioThread {
            val stored = settingsDao.findFirstOrNull()
            // Writing the unchanged preselection is how the import was undone:
            // the picker opens on the imported currency, so tapping "Set" must
            // be a no-op rather than a silent overwrite of the same value.
            if (stored != null && !shouldPersistCurrency(stored.currency, baseCurrency.code)) {
                return@ioThread
            }
            settingsWriter.save(
                (stored ?: Settings(
                    theme = Theme.LIGHT,
                    name = "",
                    baseCurrency = baseCurrency.code,
                    bufferAmount = 1000.0.toBigDecimal()
                ).toEntity()).copy(currency = baseCurrency.code)
            )
        }
        _currency.value = baseCurrency
        router.currencyNext()
    }

    /** Called by the import flow when it finishes. */
    fun importFinished(success: Boolean) {
        viewModelScope.launch {
            // The import just wrote the backup's own settings, including its
            // currency. Re-read it so the next step presents IDR rather than
            // asking the user to confirm the device default over it.
            if (success) {
                adoptStoredCurrency(IvyCurrency.getDefault())
            }
            router.importFinished(success)
        }
    }

    fun importSkip() = router.importSkip()
}

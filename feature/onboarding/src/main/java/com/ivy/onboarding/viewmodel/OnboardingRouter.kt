package com.ivy.onboarding.viewmodel

import androidx.compose.runtime.MutableState
import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.profile.Profile
import com.ivy.data.profile.ProfileId
import com.ivy.data.profile.ProfileRegistry
import com.ivy.navigation.ImportScreen
import com.ivy.navigation.MainScreen
import com.ivy.navigation.Navigation
import com.ivy.navigation.OnboardingScreen
import com.ivy.onboarding.OnboardingState
import com.ivy.wallet.domain.deprecated.logic.PreloadDataLogic
import com.ivy.wallet.domain.deprecated.logic.notification.TransactionReminderLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives the four onboarding steps: splash -> import -> currency -> profile name.
 *
 * There is no account or category step. Accounts arrive with an imported backup,
 * and [PreloadDataLogic] seeds a default one otherwise - see
 * [completeOnboarding]. A fresh install is therefore usable without the removed
 * steps, which is why they could go rather than merely be hidden.
 */
class OnboardingRouter(
    private val state: MutableState<OnboardingState>,
    private val nav: Navigation,
    private val accountDao: AccountDao,
    private val sharedPrefs: SharedPrefs,
    private val transactionReminderLogic: TransactionReminderLogic,
    private val preloadDataLogic: PreloadDataLogic,
    private val profileRegistry: ProfileRegistry,
) {

    fun initBackHandling(
        screen: OnboardingScreen,
        viewModelScope: CoroutineScope,
    ) {
        nav.onBackPressed[screen] = {
            when (state.value) {
                OnboardingState.SPLASH -> true // consume; nothing behind the first screen
                OnboardingState.IMPORT -> state.value = OnboardingState.SPLASH
                OnboardingState.CURRENCY -> state.value = OnboardingState.IMPORT
                OnboardingState.PROFILE_NAME -> state.value = OnboardingState.CURRENCY
                null -> true
            }
            true
        }
    }

    // --------------------------------- Step 1 - Splash ------------------------------------------
    fun splashNext() {
        state.value = OnboardingState.IMPORT
    }
    // --------------------------------- Step 1 ---------------------------------------------------

    // --------------------------------- Step 2 - Import ------------------------------------------
    fun startImport() {
        // Only the Ivy Wallet backup path remains; the nine third-party CSV
        // importers were removed by owner decision.
        nav.navigateTo(ImportScreen(launchedFromOnboarding = true))
    }

    fun importSkip() {
        state.value = OnboardingState.CURRENCY
    }

    fun importFinished(success: Boolean) {
        // On failure the user stays put: silently advancing would drop them into an
        // empty wallet that looks exactly like a successful import.
        if (success) {
            state.value = OnboardingState.CURRENCY
        }
    }
    // --------------------------------- Step 2 ---------------------------------------------------

    // --------------------------------- Step 3 - Currency ----------------------------------------
    fun currencyNext() {
        state.value = OnboardingState.PROFILE_NAME
    }
    // --------------------------------- Step 3 ---------------------------------------------------

    // --------------------------------- Step 4 - Profile name -----------------------------------
    fun setProfileNameNext(id: ProfileId, rawName: String, scope: CoroutineScope) {
        val name = rawName.trim().ifBlank { id.value }
        profileRegistry.register(Profile(id = id, name = name))
        completeOnboarding(scope)
    }

    private fun completeOnboarding(scope: CoroutineScope) {
        sharedPrefs.putBoolean(SharedPrefs.ONBOARDING_COMPLETED, true)

        navigateOutOfOnboarding()

        // Seeding and reminders are not UI, so they must not block the transition -
        // but they run on the caller's scope so they die with the ViewModel.
        scope.launch {
            // A fresh install that skipped the import still needs somewhere to record
            // money, so seed a default account and its categories. Harmless after an
            // import, which brought its own.
            if (accountDao.findAll().isEmpty()) {
                preloadDataLogic.preloadAccounts()
                preloadDataLogic.preloadCategories()
            }
            transactionReminderLogic.scheduleReminder()
        }
    }
    // --------------------------------- Step 4 ---------------------------------------------------

    private fun navigateOutOfOnboarding() {
        nav.resetBackStack()
        nav.navigateTo(MainScreen)
        state.value = OnboardingState.SPLASH
    }
}

package com.ivy.onboarding

import com.ivy.data.profile.Profile
import com.ivy.data.profile.ProfileId
import com.ivy.wallet.domain.data.IvyCurrency

sealed interface OnboardingEvent {

    /** Leave the splash, with or without importing anything. */
    data object SplashNext : OnboardingEvent

    data object StartImport : OnboardingEvent
    data object ImportSkip : OnboardingEvent
    data class ImportFinished(val success: Boolean) : OnboardingEvent

    data class SetBaseCurrency(val baseCurrency: IvyCurrency) : OnboardingEvent

    /**
     * Names the profile this device's data belongs to.
     *
     * [id] is validated by [ProfileId]; a blank name falls back to the default so
     * the field cannot block onboarding.
     */
    data class SetProfileName(val name: String, val id: ProfileId) : OnboardingEvent
}
package com.ivy.onboarding

import androidx.compose.runtime.Immutable
import com.ivy.data.profile.ProfileId
import com.ivy.wallet.domain.data.IvyCurrency

@Immutable
data class OnboardingDetailState(
    val currency: IvyCurrency,
    val profileId: ProfileId,
    val profileName: String,
)

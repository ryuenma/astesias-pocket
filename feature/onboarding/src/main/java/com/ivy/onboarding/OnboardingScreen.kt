package com.ivy.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.data.profile.ProfileId
import com.ivy.legacy.utils.onScreenStart
import com.ivy.navigation.OnboardingScreen
import com.ivy.onboarding.steps.OnboardingImportChoice
import com.ivy.onboarding.steps.OnboardingProfileName
import com.ivy.onboarding.steps.OnboardingSetCurrency
import com.ivy.onboarding.steps.OnboardingSplash
import com.ivy.onboarding.viewmodel.OnboardingViewModel

/**
 * Four steps, in order: welcome -> import -> currency -> profile name.
 *
 * The account and category editors used to sit between currency and the end.
 * Both arrived with an imported backup anyway, and a fresh install gets a
 * default account from PreloadDataLogic, so they are gone rather than hidden.
 */
@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.OnboardingScreen(screen: OnboardingScreen) {
    val viewModel: OnboardingViewModel = viewModel()

    val state by viewModel.state
    val uiState = viewModel.uiState()

    val isSystemDarkTheme = isSystemInDarkTheme()
    onScreenStart {
        viewModel.start(
            screen = screen,
            isSystemDarkMode = isSystemDarkTheme
        )
    }

    when (state) {
        OnboardingState.SPLASH -> {
            OnboardingSplash(
                onNext = { viewModel.onEvent(OnboardingEvent.SplashNext) }
            )
        }

        OnboardingState.IMPORT -> {
            OnboardingImportChoice(
                onImport = { viewModel.onEvent(OnboardingEvent.StartImport) },
                onSkip = { viewModel.onEvent(OnboardingEvent.ImportSkip) },
            )
        }

        OnboardingState.CURRENCY -> {
            OnboardingSetCurrency(
                preselectedCurrency = uiState.currency,
                onSetCurrency = { viewModel.onEvent(OnboardingEvent.SetBaseCurrency(it)) }
            )
        }

        OnboardingState.PROFILE_NAME -> {
            OnboardingProfileName(
                // Empty, not prefilled: a prefilled value becomes part of what the
                // user types, which produced "My pRyuenrofile".
                initialName = "",
                onNext = { name ->
                    viewModel.onEvent(
                        OnboardingEvent.SetProfileName(
                            name = name,
                            id = ProfileId.DEFAULT,
                        )
                    )
                },
            )
        }
    }
}

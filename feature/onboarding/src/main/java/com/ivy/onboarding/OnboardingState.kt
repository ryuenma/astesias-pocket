package com.ivy.onboarding

import androidx.compose.runtime.Immutable

/**
 * The four onboarding steps, in order.
 *
 * LOGIN, ACCOUNTS and CATEGORIES were removed: Google sign-in belongs to Ivy's
 * cloud, which a private sideloaded fork has no use for, and accounts and
 * categories arrive with an imported backup. A fresh install that skips the
 * import still gets a default account from `PreloadDataLogic`, so the app is
 * never left unusable.
 */
@Immutable
enum class OnboardingState {
    SPLASH,
    IMPORT,
    CURRENCY,
    PROFILE_NAME,
}
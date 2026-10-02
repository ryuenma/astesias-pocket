package com.ivy.onboarding.steps

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivy.design.l0_system.UI
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.components.OnboardingButton

/**
 * The one button style the onboarding steps use.
 *
 * Wraps the shared OnboardingButton so the steps do not each restate the same
 * text colour and gradient.
 */
@Composable
fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconStart: Int? = null,
    enabled: Boolean = true,
) {
    OnboardingButton(
        modifier = modifier.fillMaxWidth(),
        text = text,
        textColor = UI.colors.pureInverse,
        backgroundGradient = Gradient.solid(UI.colors.medium),
        iconStart = iconStart,
        enabled = enabled,
        onClick = onClick,
    )
}

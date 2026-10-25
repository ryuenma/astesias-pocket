package com.ivy.onboarding.steps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.ivyWalletCtx
import com.ivy.ui.R

/**
 * Step 1 of 4: welcome.
 *
 * One action and no hidden gestures. The previous splash hid a second state
 * behind a tap on the logo, which led to Ivy's Google sign-in - removed along
 * with the rest of the cloud login, so the toggle had nothing left to reveal.
 */
@Composable
fun BoxWithConstraintsScope.OnboardingSplash(
    onNext: () -> Unit,
) {
    val ivyContext = ivyWalletCtx()
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .systemBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(with(density) { (ivyContext.screenHeight / 3f).toDp() }))

        Image(
            modifier = Modifier.size(width = 113.dp, height = 96.dp),
            painter = painterResource(id = R.drawable.astesia_wallet_logo),
            contentScale = ContentScale.FillBounds,
            contentDescription = "Astesia’s Pocket logo",
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Astesia’s Pocket",
            textAlign = TextAlign.Center,
            style = UI.typo.h2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.your_personal_money_manager),
            textAlign = TextAlign.Center,
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.SemiBold,
            ),
        )

        Spacer(Modifier.weight(1f))

        OnboardingPrimaryButton(
            text = stringResource(R.string.get_started),
            onClick = onNext,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        OnboardingSplash(onNext = {})
    }
}

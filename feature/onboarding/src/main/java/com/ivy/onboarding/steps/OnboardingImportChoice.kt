package com.ivy.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.ui.R

/**
 * Step 2 of 4: bring your data, or don't.
 *
 * Both actions are equal weight here. Skipping is a normal choice for a second
 * phone or a genuinely fresh start, not a failure path, so it is offered as a
 * plain button rather than being buried in a toolbar.
 */
@Composable
fun BoxWithConstraintsScope.OnboardingImportChoice(
    onImport: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .systemBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.import_your_data),
            style = UI.typo.h2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.import_your_data_description),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Medium,
            ),
        )

        Spacer(Modifier.height(32.dp))

        OnboardingPrimaryButton(
            text = stringResource(R.string.import_backup_file),
            onClick = onImport,
        )

        Spacer(Modifier.height(12.dp))

        OnboardingPrimaryButton(
            text = stringResource(R.string.start_fresh),
            onClick = onSkip,
        )

        Spacer(Modifier.height(48.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        OnboardingImportChoice(onImport = {}, onSkip = {})
    }
}

package com.ivy.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.ui.R

/**
 * Step 4 of 4: name this profile.
 *
 * The name is how the profile is told apart in the switcher, so the field asks
 * for a person rather than a technical id. Blank is allowed and falls back to
 * the id - a name is a label, not a gate.
 */
@Composable
fun BoxWithConstraintsScope.OnboardingProfileName(
    initialName: String,
    onNext: (String) -> Unit,
) {
    // saveable so rotating the device does not discard what was typed
    var name by rememberSaveable { mutableStateOf(initialName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .systemBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.name_this_profile),
            style = UI.typo.h2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.name_this_profile_description),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Medium,
            ),
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.profile_name)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UI.colors.medium,
                unfocusedBorderColor = UI.colors.medium,
                focusedLabelColor = UI.colors.pureInverse,
                unfocusedLabelColor = UI.colors.pureInverse,
                focusedTextColor = UI.colors.pureInverse,
                unfocusedTextColor = UI.colors.pureInverse,
                cursorColor = UI.colors.medium,
            ),
        )

        Spacer(Modifier.weight(1f))

        OnboardingPrimaryButton(
            text = stringResource(R.string.finish),
            onClick = { onNext(name) },
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        OnboardingProfileName(initialName = "", onNext = {})
    }
}

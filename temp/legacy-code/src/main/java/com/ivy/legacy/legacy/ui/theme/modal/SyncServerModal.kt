package com.ivy.wallet.ui.theme.modal

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.IvyTitleTextField
import java.util.UUID

/**
 * Connects this device to the sync server. See [SyncConfig] for why this asks
 * for a login rather than a token.
 *
 * The password is cleared the instant it is handed to [onSignIn], so it never
 * sits in a recomposed snapshot while the request is in flight.
 */
@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Suppress("ParameterNaming")
@Composable
fun BoxWithConstraintsScope.SyncServerModal(
    visible: Boolean,
    currentUrl: String,
    currentProfile: String?,
    dismiss: () -> Unit,
    id: UUID = UUID.randomUUID(),
    /**
     * True when the stored token has run out and cannot be renewed.
     *
     * This makes the modal show the sign-in form even though a profile id is
     * stored, which is the whole point: `auth-refresh` refuses an
     * already-expired token, so the password is the only way back. Without
     * this the owner would open the modal, see "Connected as qa_a" and a
     * Disconnect button, and have no field to type into.
     */
    tokenExpired: Boolean = false,
    onSignIn: suspend (baseUrl: String, email: String, password: String) -> SyncSignInResult,
    onDisconnect: () -> Unit,
) {
    // STABLE identity for the remember keys.
    //
    // The `id` parameter defaults to UUID.randomUUID(), which is re-evaluated on
    // EVERY recomposition because the caller does not pass one. Keying remember()
    // on that value means each keystroke changes the key, the remembered state is
    // discarded, and the character vanishes the instant it is typed - the field
    // takes focus and silently swallows everything.
    //
    // The parameter is still used for IvyModal, which needs it for animation, but
    // it must never be a state key.
    val stateKey = remember { UUID.randomUUID() }

    var url by remember(stateKey) { mutableStateOf(currentUrl) }
    var email by remember(stateKey) { mutableStateOf("") }
    var password by remember(stateKey) { mutableStateOf("") }
    var working by remember(stateKey) { mutableStateOf(false) }
    var failure by remember(stateKey) { mutableStateOf<SyncSignInResult.Outcome?>(null) }
    // Kept apart from the outcome so the headline stays a stable string resource and
    // the specifics can change with the failure.
    var failureDetail by remember(stateKey) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // A screen that survives a rotation must not restore a password into a text
    // field, so the secret is dropped whenever the modal goes away.
    LaunchedEffect(visible, stateKey) {
        if (!visible) {
            password = ""
            failure = null
            failureDetail = null
        }
    }

    fun attemptSignIn() {
        if (working || url.isBlank() || email.isBlank() || password.isBlank()) return
        working = true
        failure = null
        failureDetail = null
        val secret = password
        password = ""
        scope.launch {
            val result = try {
                onSignIn(url.trim(), email.trim(), secret)
            } catch (e: Exception) {
                // Was a bare UNREACHABLE, so a throw from anywhere in the sign-in
                // chain produced the same unexplained shrug as a genuine network
                // failure. Name it: this is the last place a cause can be lost.
                SyncSignInResult(
                    SyncSignInResult.Outcome.UNREACHABLE,
                    "${e::class.java.simpleName}: ${e.message ?: "sign-in failed"}",
                )
            }
            working = false
            if (result.succeeded) dismiss() else {
                failure = result.outcome
                failureDetail = result.detail
            }
        }
    }

    val canSubmit = !working && url.isNotBlank() && email.isNotBlank() && password.isNotBlank()

    IvyModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            // Once connected there is no meaningful "save" - the honest control
            // is disconnect, which lives in the body instead.
            //
            // UNLESS the token has run out. Then the profile id is still stored
            // but nothing can be sent with it, and a modal with no primary
            // action offers the owner no way back. Signing in again is the only
            // repair, so the form has to be reachable.
            if (currentProfile == null || tokenExpired) {
                ModalSave(enabled = canSubmit) {
                    attemptSignIn()
                }
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            modifier = Modifier.padding(start = 32.dp),
            text = stringResource(R.string.sync_server),
            style = UI.typo.b1.style(
                fontWeight = FontWeight.ExtraBold,
                color = UI.colors.pureInverse
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(R.string.sync_server_explainer),
            style = UI.typo.nC.style(color = UI.colors.gray)
        )

        Spacer(Modifier.height(32.dp))

        Field(
            value = url,
            hint = stringResource(R.string.sync_server_url_hint),
            keyboard = KeyboardType.Uri,
            errorReset = { failure = null },
            onChange = { url = it }
        )
        Spacer(Modifier.height(24.dp))
        Field(
            value = email,
            hint = stringResource(R.string.sync_server_email_hint),
            // Deliberately KeyboardType.Text, NOT KeyboardType.Email.
            //
            // Some IMEs (Gboard included) render an "email" layout that hides the
            // letter keys behind an abc toggle, so the field looks like it accepts
            // nothing at all: focus lands in it, keystrokes go nowhere visible, and
            // it reads as a broken field rather than an unfamiliar keyboard.
            // A plain keyboard with autocorrect off types reliably everywhere and
            // costs nothing here - the server validates the address regardless.
            keyboard = KeyboardType.Text,
            capitalization = KeyboardCapitalization.None,
            errorReset = { failure = null },
            onChange = { email = it }
        )
        Spacer(Modifier.height(24.dp))
        Field(
            value = password,
            hint = stringResource(R.string.sync_server_password_hint),
            keyboard = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            transformation = PasswordVisualTransformation(),
            errorReset = { failure = null },
            onChange = { password = it },
            onDone = { attemptSignIn() }
        )

        if (failure != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                modifier = Modifier.padding(horizontal = 32.dp),
                // Name the ACTUAL cause. "Could not connect" blamed the network for
                // what was actually a missing account, and sent us looking in the
                // wrong place for several rounds.
                // The detail is APPENDED to the headline rather than rendered as a
                // separate Text below it. A sibling Text carrying only the detail
                // did not appear at runtime even though the cause was definitely
                // non-null and the string was present in the APK - so whatever was
                // dropping it, appending to a string that is known to render puts
                // the cause in front of the owner regardless.
                text = stringResource(failureMessageRes(failure!!)) +
                    (failureDetail?.let { "\n$it" } ?: ""),
                style = UI.typo.nC.style(
                    color = UI.colors.red,
                    fontWeight = FontWeight.Medium
                )
            )
            // The four outcomes are a summary, and the summary was not enough to
            // act on: a typo in the host, a dead route and an untrusted certificate
            // all arrive as UNREACHABLE and need three different fixes. So the
            // specific cause is shown underneath rather than left in a log nobody
            // can see.
            failureDetail?.takeIf { failure != null }?.let { detail ->
                Spacer(Modifier.height(6.dp))
                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = detail,
                    style = UI.typo.nC.style(color = UI.colors.red)
                )
            }
        }

        if (currentProfile != null) {
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = when {
                        // Say the true thing. "Connected as qa_a" while the token
                        // is dead is what made this bug invisible for days: the
                        // row looked healthy and no request could succeed.
                        tokenExpired -> stringResource(
                            R.string.sync_token_expired_modal,
                            currentProfile,
                        )
                        else -> stringResource(R.string.sync_connected_as, currentProfile)
                    },
                    style = UI.typo.nC.style(
                        color = if (tokenExpired) UI.colors.red else UI.colors.gray
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
            IvyOutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                text = stringResource(R.string.sync_disconnect),
                iconStart = null,
                borderColor = UI.colors.red,
                textColor = UI.colors.red,
                onClick = {
                    onDisconnect()
                    dismiss()
                }
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

/**
 * Outcome of a sign-in attempt, as the UI needs to see it.
 *
 * Deliberately a separate type from the data layer's result so this legacy UI
 * module stays free of the networking types.
 */
data class SyncSignInResult(
    val outcome: SyncSignInResult.Outcome,
    /**
     * WHY it failed, when the data layer could say. Shown under the generic
     * message rather than instead of it, so the headline stays stable and the
     * detail is the part that actually tells the owner what to change.
     */
    val detail: String? = null,
) {
    enum class Outcome { SUCCESS, UNREACHABLE, BAD_CREDENTIALS, SERVER_ERROR }
    val succeeded: Boolean get() = outcome == Outcome.SUCCESS
}

private fun failureMessageRes(outcome: SyncSignInResult.Outcome): Int = when (outcome) {
    // The server answered and refused. On PocketBase this is the exact signature
    // of an account that was never created, which is the overwhelmingly likely
    // cause on a server whose profiles have not been seeded yet.
    SyncSignInResult.Outcome.BAD_CREDENTIALS -> R.string.sync_err_no_account
    SyncSignInResult.Outcome.UNREACHABLE -> R.string.sync_err_unreachable
    SyncSignInResult.Outcome.SERVER_ERROR -> R.string.sync_err_server
    SyncSignInResult.Outcome.SUCCESS -> R.string.sync_err_server
}

/** One labelled text field, wrapping the legacy component's long signature. */
@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
private fun ColumnScope.Field(
    value: String,
    hint: String,
    keyboard: KeyboardType,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    transformation: VisualTransformation = VisualTransformation.None,
    errorReset: () -> Unit,
    onChange: (String) -> Unit,
    onDone: () -> Unit = {},
) {
    Column {
    IvyTitleTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        dividerModifier = Modifier.padding(horizontal = 24.dp),
        value = selectEndTextFieldValue(value),
        hint = hint,
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(
            autoCorrect = false,
            keyboardType = keyboard,
            imeAction = ImeAction.Done,
            capitalization = capitalization
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    ) {
        onChange(it.text)
        errorReset()
    }
    }
}

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        SyncServerModal(
            visible = true,
            currentUrl = "https://astesias-pb.fly.dev",
            currentProfile = null,
            dismiss = {},
            onSignIn = { _, _, _ -> SyncSignInResult(SyncSignInResult.Outcome.BAD_CREDENTIALS) },
            onDisconnect = {},
        )
    }
}
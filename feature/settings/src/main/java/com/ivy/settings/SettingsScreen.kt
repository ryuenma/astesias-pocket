package com.ivy.settings

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.legacy.Theme
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.l1_buildingBlocks.IconScale
import com.ivy.design.l1_buildingBlocks.IvyIconScaled
import com.ivy.design.utils.thenIf
import com.ivy.legacy.Constants
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.rootScreen
import com.ivy.legacy.utils.drawColoredShadow
import com.ivy.navigation.AttributionsScreen
import com.ivy.navigation.FeaturesScreen
import com.ivy.navigation.ImportScreen
import com.ivy.navigation.Navigation
import com.ivy.navigation.PendingTransfersScreen
import com.ivy.navigation.TransferAnswersScreen
import com.ivy.data.sync.DirectoryAuthApi
import com.ivy.data.sync.SyncConfig
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.domain.data.IvyCurrency
import com.ivy.wallet.ui.theme.Blue
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.GradientGreen
import com.ivy.wallet.ui.theme.GradientIvy
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.MediumBlack
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.Red3
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.IvySwitch
import com.ivy.wallet.ui.theme.components.IvyToolbar
import com.ivy.wallet.ui.theme.modal.ChooseStartDateOfMonthModal
import com.ivy.wallet.ui.theme.modal.CurrencyModal
import com.ivy.wallet.ui.theme.modal.DeleteModal
import com.ivy.wallet.ui.theme.modal.NameModal
import com.ivy.wallet.ui.theme.modal.SyncServerModal
import com.ivy.wallet.ui.theme.modal.SyncSignInResult
import com.ivy.wallet.ui.theme.modal.ProgressModal
import java.util.Locale

@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.SettingsScreen() {
    val viewModel: SettingsViewModel = screenScopedViewModel()
    val uiState = viewModel.uiState()
    val rootScreen = rootScreen()
    val context = LocalContext.current

    NotificationsPermissionEffect(viewModel)

    UI(
        currencyCode = uiState.currencyCode,
        theme = uiState.currentTheme,
        onSwitchTheme = {
            viewModel.onEvent(SettingsEvent.SwitchTheme)
        },
        lockApp = uiState.lockApp,
        showNotifications = uiState.showNotifications,
        notificationsBlocked = uiState.notificationsBlocked,
        notificationsPermanentlyDenied = uiState.notificationsPermanentlyDenied,
        pendingTransfersCount = uiState.pendingTransfersCount,
        unacknowledgedAnswersCount = uiState.unacknowledgedAnswersCount,
        hideCurrentBalance = uiState.hideCurrentBalance,
        hideIncome = uiState.hideIncome,
        progressState = uiState.progressState,
        treatTransfersAsIncomeExpense = uiState.treatTransfersAsIncomeExpense,
        nameLocalAccount = uiState.name,
        startDateOfMonth = uiState.startDateOfMonth.toInt(),
        languageOptionVisible = uiState.languageOptionVisible,
        onSetCurrency = {
            viewModel.onEvent(SettingsEvent.SetCurrency(it))
        },
        onSetName = {
            viewModel.onEvent(SettingsEvent.SetName(it))
        },
        onBackupData = {
            viewModel.onEvent(SettingsEvent.BackupData(rootScreen))
        },
        onExportToCSV = {
            viewModel.onEvent(SettingsEvent.ExportToCsv(rootScreen))
        },
        onExportToXlsx = {
            viewModel.onEvent(SettingsEvent.ExportToXlsx(rootScreen))
        },
        onSetLockApp = {
            viewModel.onEvent(SettingsEvent.SetLockApp(it))
        },
        onSetShowNotifications = {
            viewModel.onEvent(SettingsEvent.SetShowNotifications(it))
        },
        onOpenNotificationSettings = {
            viewModel.openNotificationSettings(context)
        },
        onSetHideCurrentBalance = {
            viewModel.onEvent(SettingsEvent.SetHideCurrentBalance(it))
        },
        onSetHideIncome = {
            viewModel.onEvent(SettingsEvent.SetHideIncome(it))
        },
        onSetStartDateOfMonth = {
            viewModel.onEvent(SettingsEvent.SetStartDateOfMonth(it))
        },
        onSetTreatTransfersAsIncExp = {
            viewModel.onEvent(SettingsEvent.SetTransfersAsIncomeExpense(it))
        },
        onDeleteAllUserData = {
            viewModel.onEvent(SettingsEvent.DeleteAllUserData)
        },
        onDeleteCloudUserData = {
            viewModel.onEvent(SettingsEvent.DeleteCloudUserData)
        },
        onSwitchLanguage = {
            viewModel.onEvent(SettingsEvent.SwitchLanguage)
        },
        syncBaseUrl = uiState.syncBaseUrl,
        syncProfile = uiState.syncProfile,
        syncTokenExpired = uiState.syncTokenExpired,
        onSyncSignIn = { url, email, password ->
            // Map the data layer's typed outcome onto the UI's own type, so the
            // legacy modal never has to import the networking layer.
            viewModel.signInToSync(url, email, password).let {
                SyncSignInResult(
                    when (it.outcome) {
                        DirectoryAuthApi.Outcome.SUCCESS -> SyncSignInResult.Outcome.SUCCESS
                        DirectoryAuthApi.Outcome.UNREACHABLE -> SyncSignInResult.Outcome.UNREACHABLE
                        DirectoryAuthApi.Outcome.BAD_CREDENTIALS -> SyncSignInResult.Outcome.BAD_CREDENTIALS
                        DirectoryAuthApi.Outcome.SERVER_ERROR -> SyncSignInResult.Outcome.SERVER_ERROR
                    },
                    // Carried through so the modal can say WHICH network problem it
                    // was. Without it every failure reads as one shrug.
                    detail = it.detail,
                )
            }
        },
        onSyncDisconnect = {
            viewModel.disconnectSync()
        }
    )
}

@ExperimentalFoundationApi
@Composable
@Suppress("LongMethod")
private fun BoxWithConstraintsScope.UI(
    currencyCode: String,
    theme: Theme,
    onSwitchTheme: () -> Unit,
    lockApp: Boolean,
    nameLocalAccount: String?,
    languageOptionVisible: Boolean,
    onSetCurrency: (String) -> Unit,
    startDateOfMonth: Int = 1,
    showNotifications: Boolean = true,
    notificationsBlocked: Boolean = false,
    notificationsPermanentlyDenied: Boolean = false,
    pendingTransfersCount: Int = 0,
    unacknowledgedAnswersCount: Int = 0,
    hideCurrentBalance: Boolean = false,
    hideIncome: Boolean = false,
    progressState: Boolean = false,
    treatTransfersAsIncomeExpense: Boolean = false,
    onSetName: (String) -> Unit = {},
    onBackupData: () -> Unit = {},
    onExportToCSV: () -> Unit = {},
    onExportToXlsx: () -> Unit = {},
    onSetLockApp: (Boolean) -> Unit = {},
    onSetShowNotifications: (Boolean) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onSetTreatTransfersAsIncExp: (Boolean) -> Unit = {},
    onSetHideCurrentBalance: (Boolean) -> Unit = {},
    onSetHideIncome: (Boolean) -> Unit = {},
    onSetStartDateOfMonth: (Int) -> Unit = {},
    onDeleteAllUserData: () -> Unit = {},
    onDeleteCloudUserData: () -> Unit = {},
    onSwitchLanguage: () -> Unit = {},
    syncBaseUrl: String = "",
    syncProfile: String? = null,
    syncTokenExpired: Boolean = false,
    onSyncSignIn: suspend (String, String, String) -> SyncSignInResult =
        { _, _, _ -> SyncSignInResult(SyncSignInResult.Outcome.SERVER_ERROR) },
    onSyncDisconnect: () -> Unit = {}
) {
    var currencyModalVisible by remember { mutableStateOf(false) }
    var nameModalVisible by remember { mutableStateOf(false) }
    var syncModalVisible by remember { mutableStateOf(false) }
    var chooseStartDateOfMonthVisible by remember { mutableStateOf(false) }
    var deleteCloudDataModalVisible by remember { mutableStateOf(false) }
    var deleteAllDataModalVisible by remember { mutableStateOf(false) }
    var deleteAllDataModalFinalVisible by remember { mutableStateOf(false) }
    val nav = navigation()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("settings_lazy_column")
    ) {
        stickyHeader {
            IvyToolbar(
                onBack = { nav.onBackPressed() },
            ) {
                Spacer(Modifier.weight(1f))

                val rootScreen = rootScreen()
                Text(
                    text = "${rootScreen.buildVersionName} (${rootScreen.buildVersionCode})",
                    style = UI.typo.nC.style(
                        color = UI.colors.gray,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(Modifier.width(32.dp))
            }
            // onboarding toolbar include paddingBottom 16.dp
        }

        item {
            Spacer(Modifier.height(8.dp))

            Text(
                modifier = Modifier.padding(start = 32.dp),
                text = stringResource(R.string.settings),
                style = UI.typo.h2.style(
                    fontWeight = FontWeight.Black
                )
            )

            Spacer(Modifier.height(24.dp))

            CurrencyButton(currency = currencyCode) {
                currencyModalVisible = true
            }

            Spacer(Modifier.height(12.dp))

            AccountCard(
                nameLocalAccount = nameLocalAccount,
            ) {
                nameModalVisible = true
            }

            Spacer(Modifier.height(12.dp))

            SyncServerRow(
                baseUrl = syncBaseUrl,
                profile = syncProfile,
                tokenExpired = syncTokenExpired,
                onClick = { syncModalVisible = true }
            )

            Spacer(Modifier.height(12.dp))

            // The entry point the inbox never had. Placed beside "Sync with your
            // partner" because that is the feature it belongs to: a transfer can
            // only arrive here if the two devices are paired in the first place.
            PendingTransfersRow(
                pendingCount = pendingTransfersCount
            ) {
                nav.navigateTo(PendingTransfersScreen)
            }

            Spacer(Modifier.height(12.dp))

            // The other direction of the same feature. Without it the owner
            // sends money, hears nothing back, and has no way to tell "still
            // waiting" from "declined and never told me" - which is exactly what
            // used to happen, because the answer was fetched and discarded.
            TransferAnswersRow(
                unacknowledgedCount = unacknowledgedAnswersCount
            ) {
                nav.navigateTo(TransferAnswersScreen)
            }

//            Spacer(Modifier.height(20.dp))
//            Premium()
        }

        item {
            SettingsSectionDivider(text = stringResource(R.string.import_export))

            Spacer(Modifier.height(16.dp))

            val nav = navigation()
            ExportCSV {
                onExportToCSV()
            }

            Spacer(Modifier.height(12.dp))

            Spacer(Modifier.height(12.dp))

            SettingsDefaultButton(
                // Same icon as the CSV row: it is a generic export glyph, and
                // inventing a near-duplicate vector for one button is noise.
                icon = R.drawable.ic_export_csv,
                text = stringResource(R.string.export_xlsx),
                iconPadding = 8.dp
            ) {
                onExportToXlsx()
            }

            Spacer(Modifier.height(12.dp))

            SettingsDefaultButton(
                icon = R.drawable.ic_vue_security_shield,
                text = stringResource(R.string.backup_data),
                iconPadding = 8.dp
            ) {
                onBackupData()
            }

            Spacer(Modifier.height(12.dp))

            SettingsPrimaryButton(
                icon = R.drawable.ic_export_csv,
                text = stringResource(R.string.import_data),
                backgroundGradient = GradientGreen
            ) {
                nav.navigateTo(
                    ImportScreen(
                        launchedFromOnboarding = false
                    )
                )
            }
        }

        item {
            SettingsSectionDivider(text = stringResource(R.string.app_settings))

            Spacer(Modifier.height(16.dp))

            AppThemeButton(
                icon = when (theme) {
                    Theme.LIGHT -> R.drawable.home_more_menu_light_mode
                    Theme.DARK -> R.drawable.home_more_menu_dark_mode
                    Theme.AMOLED_DARK -> R.drawable.home_more_menu_amoled_dark_mode
                    Theme.AUTO -> R.drawable.home_more_menu_auto_mode
                },
                label = when (theme) {
                    Theme.LIGHT -> stringResource(R.string.light_mode)
                    Theme.DARK -> stringResource(R.string.dark_mode)
                    Theme.AMOLED_DARK -> stringResource(R.string.amoled_mode)
                    Theme.AUTO -> stringResource(R.string.auto_mode)
                }
            ) {
                onSwitchTheme()
            }

            Spacer(Modifier.height(12.dp))

            val nav = navigation()
//            SettingsDefaultButton(
//                icon = R.drawable.ic_custom_atom_m,
//                text = "Features"
//            ) {
//                nav.navigateTo(FeaturesScreen)
//            }
//
//            Spacer(Modifier.height(12.dp))

            if (languageOptionVisible) {
                SettingsDefaultButton(
                    icon = R.drawable.ic_vue_location_global,
                    iconPadding = 6.dp,
                    text = stringResource(R.string.language),
                    description = Locale.getDefault().displayName
                ) {
                    onSwitchLanguage()
                }

                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = lockApp,
                onSetLockApp = onSetLockApp,
                text = stringResource(R.string.lock_app),
                icon = R.drawable.ic_custom_fingerprint_m
            )

            Spacer(Modifier.height(12.dp))

            NotificationSwitch(
                showNotifications = showNotifications,
                notificationsBlocked = notificationsBlocked,
                notificationsPermanentlyDenied = notificationsPermanentlyDenied,
                onSetShowNotifications = onSetShowNotifications,
                onOpenNotificationSettings = onOpenNotificationSettings,
            )

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = hideCurrentBalance,
                onSetLockApp = onSetHideCurrentBalance,
                text = stringResource(R.string.hide_balance),
                description = stringResource(R.string.hide_balance_description),
                icon = R.drawable.ic_hide_m
            )

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = hideIncome,
                onSetLockApp = onSetHideIncome,
                text = stringResource(R.string.hide_income),
                description = stringResource(R.string.hide_income_description),
                icon = R.drawable.ic_hide_m
            )

            Spacer(Modifier.height(12.dp))

            AppSwitch(
                lockApp = treatTransfersAsIncomeExpense,
                onSetLockApp = onSetTreatTransfersAsIncExp,
                text = stringResource(R.string.transfers_as_income_expense),
                description = stringResource(R.string.transfers_as_income_expense_description),
                icon = R.drawable.ic_custom_transfer_m
            )

            Spacer(Modifier.height(12.dp))

            StartDateOfMonth(
                startDateOfMonth = startDateOfMonth
            ) {
                chooseStartDateOfMonthVisible = true
            }

            Spacer(Modifier.height(12.dp))

            CustomFeatures(
                onClick = { nav.navigateTo(FeaturesScreen) }
            )
        }

//        item {
//            SettingsSectionDivider(text = stringResource(R.string.experimental))
//
//            Spacer(Modifier.height(16.dp))
//
//            val nav = navigation()
//            SettingsDefaultButton(
//                icon = R.drawable.ic_custom_atom_m,
//                text = stringResource(R.string.experimental_settings)
//            ) {
//                nav.navigateTo(ExperimentalScreen)
//            }
//        }

        item {
            SettingsSectionDivider(text = stringResource(R.string.other))

            Spacer(Modifier.height(16.dp))

            // Kept deliberately: GPL-3.0 requires attribution to the upstream
            // project, and this button is the only surviving link to it.
            val rootScreen = rootScreen()

            SettingsPrimaryButton(
                icon = R.drawable.github_logo,
                iconPadding = 10.dp,
                text = stringResource(R.string.ivy_wallet_is_opensource),
                backgroundGradient = Gradient.solid(MediumBlack)
            ) {
                rootScreen.openUrlInBrowser(url = Constants.URL_IVY_WALLET_REPO)
            }
        }

        item {
            SettingsSectionDivider(text = stringResource(R.string.product))

            Spacer(Modifier.height(12.dp))

            Spacer(Modifier.height(16.dp))

            HelpCenter()

            Spacer(Modifier.height(12.dp))


            Spacer(Modifier.height(12.dp))

            ReportBug()

            Spacer(Modifier.height(12.dp))

            val rootActivity = rootScreen()
            RequestFeature {
                rootActivity.openUrlInBrowser(Constants.URL_GITHUB_NEW_ISSUE)
            }

            Spacer(Modifier.height(12.dp))

            Spacer(Modifier.height(12.dp))


            Spacer(Modifier.height(12.dp))

            Attributions()

            Spacer(Modifier.height(12.dp))

            TCAndPrivacyPolicy()
        }

        item {
            SettingsSectionDivider(
                text = stringResource(R.string.danger_zone),
                color = Red
            )

            Spacer(Modifier.height(16.dp))

            SettingsPrimaryButton(
                icon = R.drawable.ic_delete,
                text = stringResource(R.string.delete_all_user_data),
                backgroundGradient = Gradient.solid(Red)
            ) {
                deleteAllDataModalVisible = true
            }
        }

        item {
            Spacer(modifier = Modifier.height(120.dp)) // last item spacer
        }
    }

    CurrencyModal(
        title = stringResource(R.string.set_currency),
        initialCurrency = IvyCurrency.fromCode(currencyCode),
        visible = currencyModalVisible,
        dismiss = { currencyModalVisible = false }
    ) {
        onSetCurrency(it)
    }

    SyncServerModal(
        visible = syncModalVisible,
        // Pre-filled with the owner's own deployed server so the common case is
        // typing an email and a password, not transcribing a 35-character URL on
        // a phone keyboard. Still fully editable, because a personal fork should
        // be able to point at a different host - and a WRONG prefilled value is
        // the risk here, so this one is verified live before being used.
        currentUrl = syncBaseUrl.ifBlank { DEFAULT_SYNC_URL },
        currentProfile = syncProfile,
        tokenExpired = syncTokenExpired,
        dismiss = { syncModalVisible = false },
        onSignIn = onSyncSignIn,
        onDisconnect = onSyncDisconnect
    )

    NameModal(
        visible = nameModalVisible,
        name = nameLocalAccount ?: "",
        dismiss = { nameModalVisible = false }
    ) {
        onSetName(it)
    }

    ChooseStartDateOfMonthModal(
        visible = chooseStartDateOfMonthVisible,
        selectedStartDateOfMonth = startDateOfMonth,
        dismiss = { chooseStartDateOfMonthVisible = false }
    ) {
        onSetStartDateOfMonth(it)
    }

    DeleteModal(
        title = stringResource(R.string.delete_all_user_data_question),
        description = stringResource(
            R.string.delete_all_user_data_warning,
            stringResource(R.string.your_account)
        ),
        visible = deleteAllDataModalVisible,
        dismiss = { deleteAllDataModalVisible = false },
        onDelete = {
            deleteAllDataModalVisible = false
            deleteAllDataModalFinalVisible = true
        }
    )

    DeleteModal(
        title = stringResource(
            R.string.confirm_all_userd_data_deletion,
            stringResource(R.string.all_of_your_data)
        ),
        description = stringResource(R.string.final_deletion_warning),
        visible = deleteAllDataModalFinalVisible,
        dismiss = { deleteAllDataModalFinalVisible = false },
        onDelete = {
            onDeleteAllUserData()
        }
    )

    DeleteModal(
        title = stringResource(R.string.delete_all_cloud_data_question),
        description = stringResource(
            R.string.delete_all_user_cloud_data_warning,
            stringResource(R.string.your_account)
        ),
        visible = deleteCloudDataModalVisible,
        dismiss = { deleteCloudDataModalVisible = false },
        onDelete = {
            onDeleteCloudUserData()
            deleteCloudDataModalVisible = false
        }
    )

    ProgressModal(
        title = stringResource(R.string.exporting_data),
        description = stringResource(R.string.exporting_data_description),
        visible = progressState
    )
}

@Composable
private fun StartDateOfMonth(
    startDateOfMonth: Int,
    onClick: () -> Unit
) {
    SettingsButtonRow(
        onClick = onClick
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = R.drawable.ic_custom_calendar_m,
            tint = UI.colors.pureInverse,
            iconScale = IconScale.M,
            padding = 2.dp
        )

        Spacer(Modifier.width(8.dp))

        Text(
            modifier = Modifier.padding(vertical = 20.dp),
            text = stringResource(R.string.start_date_of_month),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = startDateOfMonth.toString(),
            style = UI.typo.nB2.style(
                fontWeight = FontWeight.ExtraBold,
                color = UI.colors.pureInverse
            )
        )

        Spacer(Modifier.width(32.dp))
    }
}

@Composable
private fun CustomFeatures(
    onClick: () -> Unit
) {
    SettingsButtonRow(
        onClick = onClick
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = R.drawable.ic_custom_programming_m,
            tint = UI.colors.pureInverse,
            iconScale = IconScale.M,
            padding = 0.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            modifier = Modifier.padding(vertical = 20.dp),
            text = stringResource(R.string.advanced_features),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )
    }
}


@Composable
private fun HelpCenter() {
    val uriHandler = LocalUriHandler.current
    SettingsDefaultButton(
        icon = R.drawable.ic_custom_education_m,
        text = stringResource(R.string.help_center),
    ) {
        uriHandler.openUri(Constants.URL_HELP_CENTER)
    }
}

@Composable
private fun ReportBug() {
    val uriHandler = LocalUriHandler.current
    SettingsDefaultButton(
        icon = R.drawable.ic_vue_dev_arrow,
        text = stringResource(R.string.report_bug),
        iconPadding = 10.dp,
    ) {
        uriHandler.openUri(Constants.URL_GITHUB_NEW_ISSUE)
    }
}

@Composable
private fun RequestFeature(
    onClick: () -> Unit
) {
    SettingsDefaultButton(
        icon = R.drawable.ic_custom_programming_m,
        text = stringResource(R.string.request_a_feature),
    ) {
        onClick()
    }
}

@Composable
private fun Attributions() {
    val nav = navigation()

    SettingsDefaultButton(
        icon = R.drawable.ic_vue_location_global,
        text = stringResource(R.string.attributions),
        iconPadding = 6.dp
    ) {
        nav.navigateTo(AttributionsScreen)
    }
}

@Composable
private fun AppThemeButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit
) {
    SettingsPrimaryButton(
        icon = icon,
        text = label,
        backgroundGradient = Gradient.solid(UI.colors.medium),
        textColor = UI.colors.pureInverse,
        iconPadding = 6.dp,
        description = stringResource(R.string.tap_to_switch_theme),
        onClick = onClick
    )
}

/**
 * Owns the POST_NOTIFICATIONS request for the settings screen.
 *
 * This is the ONLY place in the app that asks for the permission, and it asks
 * for one reason: the user switched notifications on. There is no prompt on
 * launch, after onboarding, or on the first transaction - a request the user
 * did not ask for is a request they dismiss without reading, and the answer is
 * effectively final.
 *
 * The launcher is registered here, in the composition, rather than inside the
 * click handler: registration has to happen before the lifecycle reaches
 * STARTED, and a launcher built inside the handler is too late.
 *
 * The rationale bit is re-read on every resume rather than cached, because the
 * user can grant or revoke the permission in system settings while the app is
 * backgrounded - and auto-reset revokes it outright for anyone who has not
 * opened the app in months.
 */
@Composable
private fun NotificationsPermissionEffect(
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val permission = Manifest.permission.POST_NOTIFICATIONS

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    // The user just switched notifications on and the OS grant is missing, so
    // the system dialog is what should go up. Keyed on the flag itself, so it
    // fires once per request rather than on every recomposition.
    val requesting = viewModel.notificationPermissionRequestPending()
    LaunchedEffect(requesting) {
        if (requesting) {
            if (viewModel.requiresNotificationPermissionRequest()) {
                launcher.launch(permission)
            } else {
                // Below API 33 there is nothing to ask for, so the toggle is
                // already honoured. Report it as satisfied rather than leaving
                // the row stuck claiming reminders are blocked.
                viewModel.onNotificationPermissionResult(granted = true)
            }
        }
    }

    // Re-read on entry and on every resume: the user can grant or revoke the
    // permission in system settings while this screen is in the background, and
    // auto-reset revokes it outright for anyone who rarely opens the app.
    fun refresh() {
        viewModel.refreshNotificationPermission(
            activity.shouldShowRequestPermissionRationale(permission)
        )
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(activity) {
        val listener = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        activity.lifecycle.addObserver(listener)
        onDispose { activity.lifecycle.removeObserver(listener) }
    }
}

/**
 * Walks the Context chain to the hosting Activity, if there is one.
 *
 * Typed as [ComponentActivity] rather than [Activity] because only the
 * androidx Activity hierarchy carries a Lifecycle, and the resume hook below
 * needs one to observe.
 */
private fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * The notifications row.
 *
 * When reminders cannot be delivered, the switch stays on - the user asked for
 * them - and the row says so underneath instead of claiming a working feature.
 * Tapping the row in that state opens the app's notification settings, because
 * a denied permission cannot be re-requested by the app and sending the user
 * nowhere leaves them with no way to fix it.
 */
@Composable
private fun NotificationSwitch(
    showNotifications: Boolean,
    notificationsBlocked: Boolean,
    notificationsPermanentlyDenied: Boolean,
    onSetShowNotifications: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    Column {
        AppSwitch(
            lockApp = showNotifications,
            onSetLockApp = onSetShowNotifications,
            text = stringResource(R.string.show_notifications),
            icon = R.drawable.ic_notification_m,
            description = if (notificationsBlocked) {
                stringResource(
                    if (notificationsPermanentlyDenied) {
                        R.string.notifications_blocked_open_settings
                    } else {
                        R.string.notifications_blocked_retry
                    }
                )
            } else {
                ""
            },
        )

        if (notificationsBlocked) {
            // Actionable, not decorative: this is the route that actually changes
            // the outcome once the system will no longer prompt.
            SettingsDefaultButton(
                icon = R.drawable.ic_notification_m,
                text = stringResource(R.string.open_notification_settings),
                iconPadding = 8.dp
            ) {
                onOpenNotificationSettings()
            }
        }
    }
}

@Composable
private fun AppSwitch(
    lockApp: Boolean,
    onSetLockApp: (Boolean) -> Unit,
    text: String,
    icon: Int,
    description: String = "",
) {
    SettingsButtonRow(
        onClick = {
            onSetLockApp(!lockApp)
        }
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = icon,
            tint = UI.colors.pureInverse,
            iconScale = IconScale.M,
            padding = 0.dp
        )

        Spacer(Modifier.width(8.dp))

        Column(
            Modifier
                .weight(1f)
                .padding(top = 20.dp, bottom = 20.dp, end = 8.dp)
        ) {
            Text(
                text = text,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold
                )
            )
            if (description.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(end = 8.dp),
                    text = description,
                    style = UI.typo.nB2.style(
                        color = Gray,
                        fontWeight = FontWeight.Normal
                    ).copy(fontSize = 14.sp)
                )
            }
        }

        // Spacer(Modifier.weight(1f))

        IvySwitch(enabled = lockApp) {
            onSetLockApp(it)
        }

        Spacer(Modifier.width(16.dp))
    }
}

/**
 * Settings row for the partner sync server.
 *
 * Shows connection state rather than hiding it: an unconfigured app that looks
 * configured is exactly the failure that made the transfer picker confusing in
 * the first place. The same rule applies to a token that has quietly run out -
 * the pairing lasts 120 hours, and once it expires the app can still READ the
 * server (HTTP 200, zero rows) but every write is refused. So "Connected" was
 * a lie at exactly the moment it mattered, which is why the expired state gets
 * its own line rather than a colour tweak.
 */
/** Verified live 2026-10-02. Overridable in the dialog. */
private const val DEFAULT_SYNC_URL = "https://astesias-pb.fly.dev"

@Composable
private fun SyncServerRow(
    baseUrl: String,
    profile: String?,
    tokenExpired: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r2)
            .background(UI.colors.medium, UI.shapes.r2)
            .clickable { onClick() }
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_sync_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(24.dp))

            Text(
                text = stringResource(R.string.sync_server),
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Black,
                    color = UI.colors.gray
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = when {
                // Before the "connected" branch, because a profile id and a
                // usable token are two different facts and only the second one
                // means a request can succeed.
                tokenExpired -> stringResource(R.string.sync_token_expired)
                profile != null -> stringResource(R.string.sync_connected_as, profile)
                baseUrl.isNotBlank() -> baseUrl
                else -> stringResource(R.string.sync_not_connected)
            },
            style = UI.typo.nC.style(
                color = when {
                    tokenExpired -> UI.colors.red
                    profile != null -> UI.colors.green
                    else -> UI.colors.gray
                }
            )
        )

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Settings row for the partner's incoming transfers.
 *
 * Always present, never conditional on there being something to show. A row
 * that only appears when money has arrived is a row nobody learns to recognise,
 * and the whole point of this entry point is that the inbox is findable BEFORE
 * she needs it. When the count is above zero it reads as an alert; when it is
 * zero it reads as "nothing waiting", which is also worth saying.
 */
@Composable
private fun PendingTransfersRow(
    pendingCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r2)
            .background(UI.colors.medium, UI.shapes.r2)
            .clickable { onClick() }
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_pending_transfers_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(24.dp))

            Text(
                text = stringResource(R.string.pending_transfers_title),
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Black,
                    color = UI.colors.gray
                )
            )

            Spacer(Modifier.weight(1f))

            if (pendingCount > 0) {
                // A badge, not just a colour change: the number is the fact, and
                // colour alone would leave a screen-reader user with no way to
                // learn that money is waiting.
                Text(
                    text = pendingCount.toString(),
                    style = UI.typo.c.style(
                        fontWeight = FontWeight.Black,
                        color = UI.colors.green
                    )
                )

                Spacer(Modifier.width(24.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = if (pendingCount > 0) {
                stringResource(R.string.pending_transfers_waiting, pendingCount)
            } else {
                stringResource(R.string.no_pending_transfers)
            },
            style = UI.typo.nC.style(
                color = if (pendingCount > 0) UI.colors.green else UI.colors.gray
            )
        )

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Settings row for what the partner answered about transfers she sent.
 *
 * The mirror of [PendingTransfersRow], and built the same way on purpose: always
 * present, never conditional on there being something to show, so the destination
 * is findable before it is ever needed. Zero reads as "nothing new" rather than
 * hiding the row, because a row that appears only when money has been answered
 * is a row nobody learns to recognise.
 *
 * The badge is green for the same reason the inbox badge is - it counts something
 * to read, not an error. A declined answer is still something she needs to see,
 * so the count deliberately does not distinguish between the two.
 */
@Composable
private fun TransferAnswersRow(
    unacknowledgedCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r2)
            .background(UI.colors.medium, UI.shapes.r2)
            .clickable { onClick() }
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_transfer_answers_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(24.dp))

            Text(
                text = stringResource(R.string.transfer_answers_title),
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Black,
                    color = UI.colors.gray
                )
            )

            Spacer(Modifier.weight(1f))

            if (unacknowledgedCount > 0) {
                // A badge, not just a colour change: the number is the fact, and
                // colour alone leaves a screen-reader user with no way to learn
                // that an answer is waiting.
                Text(
                    text = unacknowledgedCount.toString(),
                    style = UI.typo.c.style(
                        fontWeight = FontWeight.Black,
                        color = UI.colors.green
                    )
                )

                Spacer(Modifier.width(24.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = if (unacknowledgedCount > 0) {
                stringResource(R.string.transfer_answers_waiting, unacknowledgedCount)
            } else {
                stringResource(R.string.no_transfer_answers)
            },
            style = UI.typo.nC.style(
                color = if (unacknowledgedCount > 0) UI.colors.green else UI.colors.gray
            )
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccountCard(
    nameLocalAccount: String?,
    onCardClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r2)
            .background(UI.colors.medium, UI.shapes.r2)
            .clickable {
                onCardClick()
            }
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_profile_card"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(24.dp))

            Text(
                text = stringResource(R.string.account_uppercase),
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Black,
                    color = UI.colors.gray
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        AccountCardLocalAccount(
            name = nameLocalAccount,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccountCardLocalAccount(
    name: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(20.dp))
        IvyIconScaled(
            icon = R.drawable.ic_local_account,
            iconScale = IconScale.M
        )

        Spacer(Modifier.width(12.dp))

        Text(
            modifier = Modifier
                .weight(1f)
                .testTag("local_account_name"),
            text = if (!name.isNullOrBlank()) name else stringResource(R.string.anonymous),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun ExportCSV(
    onExportToCSV: () -> Unit
) {
    SettingsDefaultButton(
        icon = R.drawable.ic_vue_pc_printer,
        text = stringResource(R.string.export_to_csv),
        iconPadding = 6.dp,
        description = stringResource(R.string.do_not_use_for_backup_purposes)
    ) {
        onExportToCSV()
    }
}

@Composable
private fun TCAndPrivacyPolicy() {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(16.dp))

        val uriHandler = LocalUriHandler.current

        Text(
            modifier = Modifier
                .weight(1f)
                .clip(UI.shapes.rFull)
                .border(2.dp, UI.colors.medium, UI.shapes.rFull)
                .clickable {
                    uriHandler.openUri(Constants.URL_TC)
                }
                .padding(vertical = 14.dp),
            text = stringResource(R.string.terms_conditions),
            style = UI.typo.c.style(
                fontWeight = FontWeight.ExtraBold,
                color = UI.colors.pureInverse,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.width(12.dp))

        Text(
            modifier = Modifier
                .weight(1f)
                .clip(UI.shapes.rFull)
                .border(2.dp, UI.colors.medium, UI.shapes.rFull)
                .clickable {
                    uriHandler.openUri(Constants.URL_PRIVACY_POLICY)
                }
                .padding(vertical = 14.dp),
            text = stringResource(R.string.privacy_policy),
            style = UI.typo.c.style(
                fontWeight = FontWeight.ExtraBold,
                color = UI.colors.pureInverse,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun SettingsPrimaryButton(
    @DrawableRes icon: Int,
    text: String,
    hasShadow: Boolean = false,
    backgroundGradient: Gradient = Gradient.solid(UI.colors.medium),
    textColor: Color = White,
    iconPadding: Dp = 0.dp,
    description: String? = null,
    onClick: () -> Unit
) {
    SettingsButtonRow(
        hasShadow = hasShadow,
        backgroundGradient = backgroundGradient,
        onClick = onClick
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = icon,
            tint = textColor,
            iconScale = IconScale.M,
            padding = iconPadding
        )

        Spacer(Modifier.width(8.dp))

        Column(
            Modifier
                .weight(1f)
                .padding(top = 20.dp, bottom = 20.dp, end = 8.dp)
        ) {
            Text(
                text = text,
                style = UI.typo.b2.style(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                )
            )
            if (!description.isNullOrEmpty()) {
                Text(
                    modifier = Modifier.padding(end = 8.dp),
                    text = description,
                    style = UI.typo.nB2.style(
                        color = Gray,
                        fontWeight = FontWeight.Normal
                    ).copy(fontSize = 14.sp)
                )
            }
        }
    }
}

@Composable
private fun SettingsButtonRow(
    onClick: (() -> Unit)?,
    hasShadow: Boolean = false,
    backgroundGradient: Gradient = Gradient.solid(UI.colors.medium),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .thenIf(hasShadow) {
                drawColoredShadow(color = backgroundGradient.startColor)
            }
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(backgroundGradient.asHorizontalBrush(), UI.shapes.r4)
            .thenIf(onClick != null) {
                clickable {
                    onClick?.invoke()
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun AccountCardButton(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(UI.colors.pure, UI.shapes.rFull)
            .clickable {
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = icon,
            iconScale = IconScale.M
        )

        Spacer(Modifier.width(4.dp))

        Text(
            modifier = Modifier
                .padding(vertical = 10.dp),
            text = text,
            style = UI.typo.b2.style(
                fontWeight = FontWeight.Bold,
                color = UI.colors.pureInverse
            )
        )

        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun CurrencyButton(
    currency: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .clickable {
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = R.drawable.ic_currency,
            iconScale = IconScale.M,
            padding = 0.dp
        )

        Spacer(Modifier.width(8.dp))

        Text(
            modifier = Modifier.padding(vertical = 20.dp),
            text = stringResource(R.string.set_currency),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = currency,
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(4.dp))

        IvyIconScaled(
            icon = R.drawable.ic_arrow_right,
            iconScale = IconScale.M
        )

        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun SettingsSectionDivider(
    text: String,
    color: Color = Gray
) {
    Column {
        Spacer(Modifier.height(32.dp))

        Text(
            modifier = Modifier.padding(start = 32.dp),
            text = text,
            style = UI.typo.b2.style(
                color = color,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun SettingsDefaultButton(
    @DrawableRes icon: Int,
    text: String,
    iconPadding: Dp = 0.dp,
    description: String? = null,
    onClick: () -> Unit,
) {
    SettingsPrimaryButton(
        icon = icon,
        text = text,
        backgroundGradient = Gradient.solid(UI.colors.medium),
        textColor = UI.colors.pureInverse,
        iconPadding = iconPadding,
        description = description
    ) {
        onClick()
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview(theme: Theme = Theme.LIGHT) {
    IvyWalletPreview(theme) {
        UI(
            nameLocalAccount = null,
            theme = Theme.AUTO,
            onSwitchTheme = {},
            lockApp = false,
            currencyCode = "BGN",
            onSetCurrency = {},
            languageOptionVisible = true
        )
    }
}

/** For screenshot testing */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsUiTest(isDark: Boolean) {
    val theme = when (isDark) {
        true -> Theme.DARK
        false -> Theme.LIGHT
    }
    Preview(theme)
}
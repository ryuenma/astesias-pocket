package com.ivy.wallet.ui.theme.modal.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.data.model.AccountType
import com.ivy.domain.legacy.ui.IvyColorPicker
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.legacy.utils.onScreenStart
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.legacy.utils.toLowerCaseLocal
import com.ivy.legacy.utils.toUpperCaseLocal
import com.ivy.ui.R
import com.ivy.wallet.domain.data.IvyCurrency
import com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.dynamicContrast
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.components.IvyCheckboxWithText
import com.ivy.wallet.ui.theme.modal.ChooseIconModal
import com.ivy.wallet.ui.theme.modal.CurrencyModal
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalAddSave
import com.ivy.wallet.ui.theme.modal.ModalAmountSection
import com.ivy.wallet.ui.theme.modal.ModalTitle
import java.util.UUID

@Deprecated("Old design system. Use `:ivy-design` and Material3")
data class AccountModalData(
    val account: Account?,
    val baseCurrency: String,
    val balance: Double,
    val adjustBalanceMode: Boolean = false,
    val forceNonZeroBalance: Boolean = false,
    val autoFocusKeyboard: Boolean = true,
    val id: UUID = UUID.randomUUID()
)

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BoxWithConstraintsScope.AccountModal(
    modal: AccountModalData?,
    onCreateAccount: (CreateAccountData) -> Unit,
    onEditAccount: (Account, balance: Double) -> Unit,
    dismiss: () -> Unit,
) {
    val account = modal?.account
    var nameTextFieldValue by remember(modal) {
        mutableStateOf(selectEndTextFieldValue(account?.name))
    }
    var color by remember(modal) {
        mutableStateOf(account?.color?.let { Color(it) } ?: Ivy)
    }
    var amount by remember(modal) {
        mutableStateOf(modal?.balance ?: 0.0)
    }
    var currencyCode by remember(modal) {
        mutableStateOf(account?.currency ?: modal?.baseCurrency ?: "")
    }
    var icon by remember(modal) {
        mutableStateOf(account?.icon)
    }
    var includeInBalance by remember(modal) {
        mutableStateOf(account?.includeInBalance ?: true)
    }
    // null means "custom": the owner picked an icon themselves, so the type row
    // shows nothing selected and the includeInBalance checkbox stands on its
    // own, as it did before types existed.
    var accountType by remember(modal) {
        mutableStateOf(typeOf(account?.icon))
    }

    var amountModalVisible by remember { mutableStateOf(false) }
    var currencyModalVisible by remember { mutableStateOf(false) }
    var chooseIconModalVisible by remember(modal) {
        mutableStateOf(false)
    }

    val forceNonZeroBalance = modal?.forceNonZeroBalance ?: false

    IvyModal(
        id = modal?.id,
        visible = modal != null,
        dismiss = dismiss,
        shiftIfKeyboardShown = false,
        PrimaryAction = {
            ModalAddSave(
                item = modal?.account,
                enabled = nameTextFieldValue.text.isNotNullOrBlank() && (!forceNonZeroBalance || amount > 0)
            ) {
                save(
                    account = account,
                    nameTextFieldValue = nameTextFieldValue,
                    currency = currencyCode,
                    color = color,
                    icon = icon,
                    amount = amount,
                    includeInBalance = includeInBalance,

                    onCreateAccount = onCreateAccount,
                    onEditAccount = onEditAccount,
                    dismiss = dismiss
                )
            }
        }
    ) {
        onScreenStart {
            if (modal?.adjustBalanceMode == true) {
                amountModalVisible = true
            }
        }

        Spacer(Modifier.height(32.dp))

        ModalTitle(
            text = if (modal?.account != null) {
                stringResource(
                    R.string.edit_account
                )
            } else {
                stringResource(R.string.new_account)
            },
        )

        Spacer(Modifier.height(24.dp))

        IconNameRow(
            hint = stringResource(R.string.account_name),
            defaultIcon = R.drawable.ic_custom_account_m,
            color = color,
            icon = icon,

            autoFocusKeyboard = modal?.autoFocusKeyboard ?: true,

            nameTextFieldValue = nameTextFieldValue,
            setNameTextFieldValue = { nameTextFieldValue = it },
            showChooseIconModal = {
                chooseIconModalVisible = true
            }
        )

        Spacer(Modifier.height(24.dp))

                AccountTypeRow(
                    selected = accountType,
                    onSelect = { type ->
                        accountType = type
                        icon = type.icon.id
                        includeInBalance = type.includeInBalance
                    }
                )

                Spacer(Modifier.height(24.dp))

                IvyColorPicker(
                    selectedColor = color,
                    onColorSelected = { color = it }
                )

        Spacer(modifier = Modifier.height(40.dp))

        ModalAmountSection(
            Header = {
                Spacer(Modifier.height(16.dp))

                AccountCurrency(
                    currencyCode = currencyCode
                ) {
                    currencyModalVisible = true
                }

                Spacer(modifier = Modifier.height(16.dp))

                IvyCheckboxWithText(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .align(Alignment.Start),
                    text = stringResource(R.string.include_account),
                    checked = includeInBalance
                ) {
                    includeInBalance = it
                }
            },
            label = stringResource(R.string.enter_account_balance).uppercase(),
            currency = currencyCode,
            amount = amount,
            amountPaddingTop = 40.dp,
            amountPaddingBottom = 40.dp,
        ) {
            amountModalVisible = true
        }
    }

    val amountModalId = remember(modal, amount) {
        UUID.randomUUID()
    }
    AmountModal(
        id = amountModalId,
        visible = amountModalVisible,
        currency = currencyCode,
        initialAmount = amount,
        showPlusMinus = true,
        dismiss = { amountModalVisible = false }
    ) { newAmount ->
        amount = newAmount

        if (modal?.adjustBalanceMode == true) {
            save(
                account = account,
                nameTextFieldValue = nameTextFieldValue,
                currency = currencyCode,
                color = color,
                icon = icon,
                amount = newAmount,
                includeInBalance = includeInBalance,

                onCreateAccount = onCreateAccount,
                onEditAccount = onEditAccount,
                dismiss = dismiss
            )
        }
    }

    val context = LocalContext.current
    CurrencyModal(
        title = stringResource(R.string.choose_currency),
        initialCurrency = IvyCurrency.fromCode(currencyCode),
        visible = currencyModalVisible,
        dismiss = { currencyModalVisible = false }
    ) {
        currencyCode = it

//        if (IvyCurrency.fromCode(it)?.isCrypto == true) {
//            if (getCustomIconId(context = context, iconName = it, size = "m") != null) {
//                icon = it
//            }
//        }
    }

    ChooseIconModal(
        visible = chooseIconModalVisible,
        initialIcon = icon ?: "account",
        color = color,
        dismiss = { chooseIconModalVisible = false }
    ) {
        icon = it
        // A hand-picked icon is the owner overriding the type, so the row
        // stops claiming to know what kind of account this is. The
        // includeInBalance checkbox is left alone - re-deciding it here would
        // silently move money between Total Balance and savings.
        accountType = typeOf(it)
    }
}

private fun save(
    account: Account?,
    nameTextFieldValue: TextFieldValue,
    currency: String,
    color: Color,
    icon: String?,
    amount: Double,
    includeInBalance: Boolean,

    onCreateAccount: (CreateAccountData) -> Unit,
    onEditAccount: (Account, balance: Double) -> Unit,
    dismiss: () -> Unit
) {
    if (account != null) {
        onEditAccount(
            account.copy(
                name = nameTextFieldValue.text.trim(),
                currency = currency,
                includeInBalance = includeInBalance,
                icon = icon,
                color = color.toArgb()
            ),
            amount
        )
    } else {
        onCreateAccount(
            CreateAccountData(
                name = nameTextFieldValue.text.trim(),
                currency = currency,
                color = color,
                icon = icon,
                balance = amount,
                includeBalance = includeInBalance
            )
        )
    }

    dismiss()
}

/**
 * The account type an already-saved icon means, or null for a custom icon.
 *
 * Delegates to the model rather than repeating the lookup, so the modal and
 * the type can never disagree about which icons are typed.
 */
private fun typeOf(iconName: String?): AccountType? = iconName?.let {
    AccountType.entries.firstOrNull { type -> type.icon.id == it }
}

/**
 * Picks what kind of account this is, which is what decides whether its money
 * is inside Total Balance.
 *
 * Deliberately a row of types rather than another free-form control: choosing
 * "Savings" is the point of the feature, and it has to set includeInBalance
 * for the owner or the choice would not mean anything.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AccountTypeRow(
    selected: AccountType?,
    onSelect: (AccountType) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 32.dp)) {
        Text(
            text = stringResource(R.string.account_type),
            style = UI.typo.b2.style(
                color = UI.colors.gray,
                fontWeight = FontWeight.SemiBold
            )
        )

        Spacer(Modifier.height(12.dp))

        // FlowRow, not Row: with three chips of unequal intrinsic width sharing one
        // line, the last one is squeezed to whatever is left and its label breaks
        // to one character per line. In Indonesian account_type_savings is
        // "Rekening Deposito" (22 chars), which is worse than the English
        // "Savings". FlowRow lets each chip take its natural width and simply
        // move to the next line instead - so the layout is correct whatever the
        // translation says, rather than correct only for the string it was
        // measured against.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("account_type_row")
        ) {
            AccountType.entries.forEach { type ->
                AccountTypeChip(
                    type = type,
                    selected = type == selected,
                    onClick = { onSelect(type) }
                )
            }
        }

        if (selected == AccountType.Savings) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.savings_account_hint),
                style = UI.typo.c.style(color = UI.colors.gray)
            )
        }
    }
}

@Composable
private fun AccountTypeChip(
    type: AccountType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = when (type) {
        AccountType.Cash -> stringResource(R.string.account_type_cash)
        AccountType.Bank -> stringResource(R.string.account_type_bank)
        AccountType.Savings -> stringResource(R.string.account_type_savings)
    }

    Row(
        modifier = Modifier
            .clip(UI.shapes.r4)
            .background(
                if (selected) Ivy else UI.colors.medium,
                UI.shapes.r4
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("account_type_${type.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemIconSDefaultIcon(
            iconName = type.icon.id,
            defaultIcon = R.drawable.ic_custom_account_s,
            tint = if (selected) Ivy.dynamicContrast() else UI.colors.pureInverse
        )

        Spacer(Modifier.width(8.dp))

        Text(
            // Belt and braces on top of the FlowRow: if a translation is ever long
            // enough that even a full row cannot hold it, ellipsize rather than
            // breaking the label into a vertical stack of single characters.
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UI.typo.b2.style(
                color = if (selected) Ivy.dynamicContrast() else UI.colors.pureInverse,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}

@Composable
private fun AccountCurrency(
    currencyCode: String,

    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(UI.colors.medium, UI.shapes.r4)
            .clip(UI.shapes.r4)
            .clickable {
                onClick()
            }
            .padding(vertical = 24.dp)
            .testTag("account_modal_currency"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(32.dp))

        Text(
            text = currencyCode.toUpperCaseLocal(),
            style = UI.typo.b1.style(
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.weight(1f))

        val currencyName = IvyCurrency.fromCode(currencyCode)?.name ?: ""
        Text(
            text = "-$currencyName".toLowerCaseLocal(),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.SemiBold,
                color = Gray
            )
        )

        Spacer(Modifier.width(24.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        AccountModal(
            modal = AccountModalData(
                account = null,
                baseCurrency = "BGN",
                balance = 0.0
            ),
            onCreateAccount = { },
            onEditAccount = { _, _ -> }
        ) {
        }
    }
}

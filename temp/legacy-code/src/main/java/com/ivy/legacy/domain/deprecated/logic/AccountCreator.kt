package com.ivy.legacy.domain.deprecated.logic

import androidx.compose.ui.graphics.toArgb
import arrow.core.raise.either
import com.ivy.base.di.AppCoroutineScope
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CurrencyRepository
import com.ivy.data.sync.DirectorySyncRepository
import com.ivy.legacy.utils.ioThread
import com.ivy.wallet.domain.deprecated.logic.WalletAccountLogic
import com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData
import com.ivy.wallet.domain.pure.util.nextOrderNum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import com.ivy.legacy.datamodel.Account as LegacyAccount

/**
 * Creates and edits the local accounts.
 *
 * ## Why publishing lives here
 *
 * This is the ONE funnel every account create and edit in the app goes through
 * - six call sites across five feature modules all land on these two functions.
 * Hooking the sync at any of the call sites instead would mean six chances to
 * forget one, and an account that exists locally but was never published is
 * invisible on the other phone with no trace anywhere that it should have been.
 *
 * The publish is fire-and-forget on the application scope: the account is
 * already committed to Room by then, so it must not be able to fail, delay, or
 * throw back into the screen that saved it.
 */
class AccountCreator @Inject constructor(
    private val accountLogic: WalletAccountLogic,
    private val accountDao: AccountDao,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val directorySync: DirectorySyncRepository,
    @AppCoroutineScope private val appScope: CoroutineScope,
) {

    /**
     * Push this device's accounts to the shared directory, off the caller's path.
     *
     * Never throws and never blocks. A dead server must not stop an owner
     * saving an account, and the next app start republishes anything missed.
     */
    private fun publishAccounts(reason: String) {
        appScope.launch {
            runCatching { directorySync.publishLocalAccounts() }
                .onFailure { Timber.e(it, "Could not publish accounts ($reason)") }
        }
    }

    suspend fun createAccount(
        data: CreateAccountData,
        onRefreshUI: suspend () -> Unit
    ) {
        ioThread {
            val account = either {
                Account(
                    id = AccountId(value = UUID.randomUUID()),
                    name = NotBlankTrimmedString.from(data.name).bind(),
                    asset = AssetCode.from(data.currency).bind(),
                    color = ColorInt(data.color.toArgb()),
                    icon = data.icon?.let(IconAsset::from)?.getOrNull(),
                    includeInBalance = data.includeBalance,
                    orderNum = accountDao.findMaxOrderNum().nextOrderNum(),
                )
            }.getOrNull() ?: return@ioThread
            accountRepository.save(account)

            val legacyAccount = LegacyAccount(
                name = data.name,
                currency = data.currency,
                color = data.color.toArgb(),
                icon = data.icon,
                includeInBalance = data.includeBalance,
                orderNum = accountDao.findMaxOrderNum().nextOrderNum(),
                isSynced = false,
                id = account.id.value
            )
            accountLogic.adjustBalance(
                account = legacyAccount,
                actualBalance = 0.0,
                newBalance = data.balance
            )
        }

        // After the local write, not before: publishing an account the server
        // would name but the local database does not have yet would let the
        // other phone offer a destination this device cannot reconcile.
        publishAccounts("account created")

        onRefreshUI()
    }

    suspend fun editAccount(
        legacyAccount: LegacyAccount,
        newBalance: Double,
        onRefreshUI: suspend () -> Unit
    ) {
        val updatedLegacyAccount = legacyAccount.copy(
            isSynced = false
        )
        ioThread {
            val account = legacyAccount.toDomainAccount(currencyRepository).getOrNull()
                ?: return@ioThread
            accountRepository.save(account)

            accountLogic.adjustBalance(
                account = updatedLegacyAccount,
                actualBalance = accountLogic.calculateAccountBalance(updatedLegacyAccount),
                newBalance = newBalance
            )
        }

        // A rename is the case that needs this: the server holds a unique index
        // on (profileId, localAccountId), so a rename can only reach the other
        // phone as an UPDATE of the existing row. A plain re-publish is refused
        // by that index and would leave the other phone showing the old name
        // forever.
        publishAccounts("account edited")

        onRefreshUI()
    }
}

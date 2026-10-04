package com.ivy.planned.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.entity.PendingBillEntity
import com.ivy.data.model.Category
import com.ivy.data.repository.CategoryRepository
import com.ivy.domain.usecase.billreminder.BillReminderInbox
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.PlannedPaymentRule
import com.ivy.legacy.utils.ioThread
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.deprecated.logic.PlannedPaymentsLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@HiltViewModel
class PlannedPaymentsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val plannedPaymentsLogic: PlannedPaymentsLogic,
    private val categoriesRepository: CategoryRepository,
    private val accountsAct: AccountsAct,
    private val billReminderInbox: BillReminderInbox,
    private val timeProvider: TimeProvider,
) : ComposeViewModel<PlannedPaymentsScreenState, PlannedPaymentsScreenEvent>() {

    private var currency by mutableStateOf("")
    private var categories by mutableStateOf<ImmutableList<Category>>(persistentListOf())
    private var accounts by mutableStateOf<ImmutableList<Account>>(persistentListOf())
    private var oneTimePlannedPayment by
        mutableStateOf<ImmutableList<PlannedPaymentRule>>(persistentListOf())
    private var recurringPlannedPayment by
        mutableStateOf<ImmutableList<PlannedPaymentRule>>(persistentListOf())
    private var oneTimeIncome by mutableDoubleStateOf(0.0)
    private var oneTimeExpenses by mutableDoubleStateOf(0.0)
    private var recurringIncome by mutableDoubleStateOf(0.0)
    private var recurringExpenses by mutableDoubleStateOf(0.0)
    private var pendingBills by mutableStateOf<ImmutableList<PendingBillEntity>>(persistentListOf())
    private var isOneTimePaymentsExpanded by mutableStateOf(true)
    private var isRecurringPaymentsExpanded by mutableStateOf(true)

    @Composable
    override fun uiState(): PlannedPaymentsScreenState {
        LaunchedEffect(Unit) {
            start()
        }

        return PlannedPaymentsScreenState(
            currency = getCurrency(),
            categories = getCategories(),
            accounts = getAccounts(),
            oneTimeIncome = getOneTimeIncome(),
            oneTimeExpenses = getOneTimeExpenses(),
            recurringExpenses = getRecurringExpenses(),
            recurringIncome = getRecurringIncome(),
            recurringPlannedPayment = getRecurringPlannedPayment(),
            oneTimePlannedPayment = getOneTimePlannedPayment(),
            isOneTimePaymentsExpanded = getOneTimePaymentsExpanded(),
            isRecurringPaymentsExpanded = getRecurringPaymentsExpanded(),
            pendingBills = getPendingBills(),
            zone = timeProvider.getZoneId()
        )
    }

    @Composable
    private fun getCurrency(): String {
        return currency
    }

    @Composable
    private fun getCategories(): ImmutableList<Category> {
        return categories
    }

    @Composable
    private fun getAccounts(): ImmutableList<Account> {
        return accounts
    }

    @Composable
    private fun getOneTimePlannedPayment(): ImmutableList<PlannedPaymentRule> {
        return oneTimePlannedPayment
    }

    @Composable
    private fun getRecurringPlannedPayment(): ImmutableList<PlannedPaymentRule> {
        return recurringPlannedPayment
    }

    @Composable
    private fun getOneTimeExpenses(): Double {
        return oneTimeExpenses
    }

    @Composable
    private fun getOneTimeIncome(): Double {
        return oneTimeIncome
    }

    @Composable
    private fun getRecurringExpenses(): Double {
        return recurringExpenses
    }

    @Composable
    private fun getRecurringIncome(): Double {
        return recurringIncome
    }

    @Composable
    private fun getRecurringPaymentsExpanded(): Boolean {
        return isRecurringPaymentsExpanded
    }

    @Composable
    private fun getPendingBills(): ImmutableList<PendingBillEntity> {
        return pendingBills
    }

    @Composable
    private fun getOneTimePaymentsExpanded(): Boolean {
        return isOneTimePaymentsExpanded
    }

    override fun onEvent(event: PlannedPaymentsScreenEvent) {
        when (event) {
            is PlannedPaymentsScreenEvent.OnOneTimePaymentsExpanded -> {
                isOneTimePaymentsExpanded = event.isExpanded
            }
            is PlannedPaymentsScreenEvent.OnRecurringPaymentsExpanded -> {
                isRecurringPaymentsExpanded = event.isExpanded
            }

            is PlannedPaymentsScreenEvent.OnPendingBillAmountChanged -> {
                adjustPendingBillAmount(event.occurrenceKey, event.amount)
            }

            is PlannedPaymentsScreenEvent.OnPendingBillDismissed -> {
                dismissPendingBill(event.occurrenceKey)
            }
        }
    }

    /**
     * Adjusts a pending bill's amount in place.
     *
     * An UPDATE scoped by the occurrence key, never an insert, so adjusting a bill
     * cannot duplicate it. The rule that produced the bill is untouched: this
     * correction applies to this month only, and next month's reminder still uses
     * the rule's figure for the owner to check again.
     */
    private fun adjustPendingBillAmount(occurrenceKey: String, amount: Double) {
        viewModelScope.launch {
            ioThread { billReminderInbox.adjustAmount(occurrenceKey, amount) }
            pendingBills = ioThread { billReminderInbox.pendingBills() }.toImmutableList()
        }
    }

    /**
     * Discards a pending bill without paying it.
     *
     * Leaves a DISMISSED tombstone rather than deleting the row, so a replayed
     * reminder for the same month cannot bring the bill back.
     */
    private fun dismissPendingBill(occurrenceKey: String) {
        viewModelScope.launch {
            ioThread { billReminderInbox.dismiss(occurrenceKey) }
            pendingBills = ioThread { billReminderInbox.pendingBills() }.toImmutableList()
        }
    }

    private fun start() {
        viewModelScope.launch {
            val settings = ioThread { settingsDao.findFirst() }
            currency = settings.currency

            categories = categoriesRepository.findAll().toImmutableList()
            accounts = accountsAct(Unit)

            oneTimePlannedPayment =
                ioThread { plannedPaymentsLogic.oneTime() }.toImmutableList()
            oneTimeIncome = ioThread { plannedPaymentsLogic.oneTimeIncome() }
            oneTimeExpenses = ioThread { plannedPaymentsLogic.oneTimeExpenses() }

            recurringPlannedPayment =
                ioThread { plannedPaymentsLogic.recurring() }.toImmutableList()
            recurringIncome = ioThread { plannedPaymentsLogic.recurringIncome() }
            recurringExpenses = ioThread { plannedPaymentsLogic.recurringExpenses() }

            // Reloaded with the rest so a bill fabricated while the app was
            // closed appears here on the next open rather than needing a
            // separate refresh path.
            pendingBills = ioThread { billReminderInbox.pendingBills() }.toImmutableList()
        }
    }
}

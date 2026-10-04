package com.ivy.planned.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.data.db.entity.PendingBillEntity
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletComponentPreview
import com.ivy.legacy.utils.uppercaseLocal
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.components.IvyButton
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bills that have been reminded about and not yet paid.
 *
 * ## Why this is not in the transaction list
 *
 * A pending bill lives in `pending_bills`, not in `transactions`, precisely so it
 * cannot be totalled into a balance, a report or an export. It is shown here
 * instead, on the Planned Payments screen, where the owner expects to find bills
 * they owe - and it is marked UNPAID so it cannot be mistaken for money that has
 * already moved.
 *
 * The UNPAID badge is not decoration. This is the one place a pending bill could
 * be misread as a real transaction, because it sits in a list next to rules that
 * look similar and shows an amount. The amount is the owner's estimate of what he
 * owes; it is not money that has left his account, and nothing in this app will
 * subtract it from his balance until he records the payment himself.
 */
@Suppress("LongParameterList")
fun LazyListScope.pendingBillItems(
    bills: List<PendingBillEntity>,
    currency: String,
    zone: ZoneId,
    onAdjustAmount: (PendingBillEntity) -> Unit,
    onDismiss: (PendingBillEntity) -> Unit,
) {
    if (bills.isEmpty()) return

    item(key = "pending-bills-header") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.bill_reminder_pending_title).uppercaseLocal(),
                style = UI.typo.h2.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // Explains WHY these are here and are not in his balance, rather
                // than leaving him to infer it from the badge.
                text = stringResource(R.string.bill_reminder_pending_empty),
                style = UI.typo.c.style(color = UI.colors.pureInverse),
            )
        }
    }

    items(
        count = bills.size,
        key = { index -> "pending-bill-${bills[index].occurrenceKey}" },
    ) { index ->
        val bill = bills[index]
        PendingBillCard(
            bill = bill,
            currency = currency,
            zone = zone,
            onAdjustAmount = { onAdjustAmount(bill) },
            onDismiss = { onDismiss(bill) },
        )
    }
}

@Composable
private fun PendingBillCard(
    bill: PendingBillEntity,
    currency: String,
    zone: ZoneId,
    onAdjustAmount: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            // Tinted rather than plain so an unpaid bill never reads as a settled
            // one at a glance. It sits in the same list as real planned payments
            // and shows the same shape of information, so the difference has to be
            // visible without reading the text.
            .background(Orange.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bill.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.bill_reminder),
                style = UI.typo.b2.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse,
                ),
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = formatDueOn(bill.dueOn, zone),
                style = UI.typo.c.style(color = UI.colors.pureInverse),
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.bill_reminder_pending_unpaid),
                style = UI.typo.c.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = Orange,
                ),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatAmount(bill.amount, currency),
                style = UI.typo.b1.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse,
                ),
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Adjust is the original requirement: the amount must be correctable
                // on the fly, because a bill is an estimate until he checks it.
                IvyButton(
                    text = stringResource(R.string.edit),
                    onClick = onAdjustAmount,
                )

                IvyButton(
                    text = stringResource(R.string.bill_reminder_dismiss),
                    onClick = onDismiss,
                )
            }
        }
    }
}

private fun formatAmount(amount: Double, currency: String): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        runCatching { this.currency = java.util.Currency.getInstance(currency) }
    }.format(amount)

private fun formatDueOn(dueOnEpochMillis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
        .format(Instant.ofEpochMilli(dueOnEpochMillis).atZone(zone).toLocalDate())

@Preview
@Composable
private fun Preview_PendingBills() {
    IvyWalletComponentPreview {
        Column {
            PendingBillCard(
                bill = PendingBillEntity(
                    occurrenceKey = "reminder:[abc]:[2027-01]",
                    ruleId = "abc",
                    dueOn = Instant.parse("2027-01-25T00:00:00Z").toEpochMilli(),
                    amount = 750_000.0,
                    title = "Internet",
                    description = null,
                    accountId = "acct",
                    type = com.ivy.base.model.TransactionType.EXPENSE,
                    state = PendingBillEntity.STATE_PENDING,
                ),
                currency = "IDR",
                zone = ZoneId.of("Asia/Jakarta"),
                onAdjustAmount = {},
                onDismiss = {},
            )
        }
    }
}
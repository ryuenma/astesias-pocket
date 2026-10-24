package com.ivy.transaction.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.data.db.entity.PendingTransferEntity
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.components.IvyBorderButton
import com.ivy.wallet.ui.theme.modal.IvyModal
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The counterpart's inbox: transfers waiting for her to confirm.
 *
 * Each row names WHO sent it and WHEN, before the amount, because the question
 * this screen answers is "is this the one I was expecting?" - a bare figure with
 * no sender is exactly the kind of claim that should not be accepted on trust.
 *
 * Nothing here is styled as a ledger row, deliberately. A pending item is not a
 * transaction, and giving it the same visual weight as one would invite the
 * reader to treat it as recorded fact.
 *
 * Accept and Decline are per-item, not per-screen: a batch action on money is a
 * footgun, and one transfer being wrong is a reason to decline that one transfer.
 */
@Composable
fun BoxScope.PendingTransfersModal(
    visible: Boolean,
    items: List<PendingTransferEntity>,
    onAccept: (PendingTransferEntity) -> Unit,
    onDecline: (PendingTransferEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    IvyModal(
        id = null,
        visible = visible,
        dismiss = onDismiss,
        shiftIfKeyboardShown = false,
        // A real control in the actions row. This used to pass a 1dp Spacer,
        // which left the row holding only the close X - and the empty state had
        // no control of its own, so "Nothing to confirm" was a dead end with no
        // labelled way out.
        PrimaryAction = {
            IvyBorderButton(
                modifier = Modifier.testTag("pending_transfers_done"),
                text = stringResource(R.string.done),
                backgroundGradient = Gradient.solid(UI.colors.mediumInverse)
            ) {
                onDismiss()
            }
        },
        // The list scrolls itself. Wrapping it in IvyModal's own ScrollState (the
        // default) gave two nested vertical scrollers, and the 400dp spacer that
        // used to sit below the list was compensating for the fight between them.
        scrollState = null,
    ) {
        Text(
            modifier = Modifier.padding(start = 32.dp, end = 32.dp),
            text = stringResource(R.string.pending_transfers_title),
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            modifier = Modifier.padding(start = 32.dp, end = 32.dp),
            text = stringResource(R.string.pending_transfers_description),
            style = UI.typo.c.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Medium,
            ),
        )

        Spacer(Modifier.height(24.dp))

        if (items.isEmpty()) {
            NothingPending()
        } else {
            LazyColumn(
                // Bounded so a long inbox scrolls rather than growing the sheet
                // past the top of the screen.
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(items, key = { it.correlationId.toString() }) { entry ->
                    PendingTransferRow(
                        entry = entry,
                        onAccept = { onAccept(entry) },
                        onDecline = { onDecline(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NothingPending() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.no_pending_transfers),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
    }
}

@Composable
private fun PendingTransferRow(
    entry: PendingTransferEntity,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${entry.recordedByProfileName} → ${entry.fromAccountName}",
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            // Date only: the owner records a date, not a time, so showing a
            // precise clock time here would imply precision the data lacks.
            text = entry.occurredOn.atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("d MMM yyyy")),
            style = UI.typo.c.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = "${formatPendingAmount(entry.amount)} ${entry.currency}",
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        entry.title?.let { title ->
            Text(
                text = title,
                style = UI.typo.c.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Spacer(Modifier.height(4.dp))

        // Two money actions that are not interchangeable: Accept writes a row
        // into her ledger, Decline only writes a tombstone and leaves the ledger
        // alone. Rendered as two bare Text labels they read as a pair of links,
        // so they are now two bordered buttons of clearly different weight -
        // filled for the action that touches the ledger, muted for the one that
        // does not.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IvyBorderButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("pending_transfer_accept"),
                text = stringResource(R.string.pending_transfer_accept),
                backgroundGradient = Gradient.solid(UI.colors.mediumInverse)
            ) {
                onAccept()
            }
            IvyBorderButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("pending_transfer_decline"),
                text = stringResource(R.string.pending_transfer_decline),
                backgroundGradient = Gradient.solid(UI.colors.gray),
                textStyle = UI.typo.b2.style(
                    color = UI.colors.gray,
                    fontWeight = FontWeight.Medium
                )
            ) {
                onDecline()
            }
        }
    }
}

private fun formatPendingAmount(amount: Double): String =
    NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = 2
    }.format(amount)
package com.ivy.transaction.answers

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
import com.ivy.data.db.entity.TransferConfirmationWithEvent
import com.ivy.data.db.entity.TransferOutboxEntity
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.components.IvyBorderButton
import com.ivy.wallet.ui.theme.modal.IvyModal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The counterpart's answers to the transfers this device sent.
 *
 * Mirrors [com.ivy.transaction.pending.PendingTransfersModal] deliberately -
 * same modal shell, same row shape, same button treatment - because these two
 * screens are the two halves of one conversation and a person who has learned
 * one should not have to learn the other.
 *
 * ## Why the answer leads, and the amount does not
 *
 * Each row opens with the answer, then names the transfer it is about, then the
 * amount. The question this screen answers is "did my money arrive?", and an
 * amount-first list makes her read every figure and compare them herself. A
 * declined transfer is also shown at full weight rather than greyed out: the
 * money left her account whatever the partner decided, and dimming the row would
 * suggest the payment did not happen.
 *
 * Nothing here is styled as a ledger row. These are answers, not transactions,
 * and the ledger says nothing about them.
 */
@Composable
// The same two findings PendingTransfersModal already carries, and for the same
// reasons: the parameter list is fixed by the IvyModal call shape, and the
// List is built by the caller from an ImmutableList each time the state changes.
@Suppress("ModifierMissing", "UnstableCollections")
fun BoxScope.TransferAnswersModal(
    visible: Boolean,
    answers: List<TransferConfirmationWithEvent>,
    onDismiss: () -> Unit,
) {
    IvyModal(
        id = null,
        visible = visible,
        dismiss = onDismiss,
        shiftIfKeyboardShown = false,
        // A real control in the actions row, for the same reason the inbox has
        // one: the empty state is a dead end without a labelled way out.
        PrimaryAction = {
            IvyBorderButton(
                modifier = Modifier.testTag("transfer_answers_done"),
                text = stringResource(R.string.done),
                backgroundGradient = Gradient.solid(UI.colors.mediumInverse)
            ) {
                onDismiss()
            }
        },
        // The list scrolls itself, same as the inbox: wrapping it in IvyModal's
        // own ScrollState gives two nested vertical scrollers.
        scrollState = null,
    ) {
        Text(
            modifier = Modifier.padding(start = 32.dp, end = 32.dp),
            text = stringResource(R.string.transfer_answers_title),
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            modifier = Modifier.padding(start = 32.dp, end = 32.dp),
            text = stringResource(R.string.transfer_answers_description),
            style = UI.typo.c.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Medium,
            ),
        )

        Spacer(Modifier.height(24.dp))

        if (answers.isEmpty()) {
            NoAnswersYet()
        } else {
            LazyColumn(
                // Bounded so a long history scrolls rather than growing the
                // sheet past the top of the screen.
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(answers, key = { it.correlationId.toString() }) { answer ->
                    TransferAnswerRow(answer = answer)
                }
            }
        }
    }
}

@Composable
private fun NoAnswersYet() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.no_transfer_answers),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
    }
}

@Composable
private fun TransferAnswerRow(answer: TransferConfirmationWithEvent) {
    val accepted = answer.decision == TransferOutboxEntity.DECISION_ACCEPTED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.testTag("transfer_answer_decision"),
                text = if (accepted) {
                    stringResource(R.string.transfer_answer_accepted)
                } else {
                    stringResource(R.string.transfer_answer_declined)
                },
                style = UI.typo.b2.style(
                    // Green for accepted and a warning tone for declined, so the
                    // answer is legible before any of the detail is read. Not
                    // red: a decline is a legitimate answer about money that has
                    // already moved, not an error.
                    color = if (accepted) UI.colors.green else UI.colors.gray,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )

            Spacer(Modifier.weight(1f))

            answer.occurredOn?.let { occurredOn ->
                Text(
                    text = parseDateOrNull(occurredOn)
                        ?.atStartOfDay(ZoneId.systemDefault())
                        ?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                        .orEmpty(),
                    style = UI.typo.c.style(
                        color = UI.colors.pureInverse,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }

        Text(
            text = "${answer.fromAccountName ?: "?"} → ${answer.toAccountName ?: "?"}",
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold,
            ),
        )

        // Null-safe rather than defaulted: the amount is only ever joined from
        // the retained outbox row, and rendering "0.00" for a transfer whose
        // figure this device cannot read would put a number on the screen that
        // is not the number that moved.
        val amount = answer.amount
        if (amount != null) {
            Text(
                text = "$amount ${answer.currency.orEmpty()}",
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
        }
    }
}

/**
 * Parses the stored `yyyy-MM-dd` date, tolerating anything unreadable.
 *
 * Returns null rather than throwing: a row whose date will not parse must still
 * render its answer and its amount, because the answer is the fact this screen
 * exists to deliver and a malformed date is not worth hiding it for.
 */
private fun parseDateOrNull(raw: String): java.time.LocalDate? =
    runCatching { java.time.LocalDate.parse(raw) }.getOrNull()

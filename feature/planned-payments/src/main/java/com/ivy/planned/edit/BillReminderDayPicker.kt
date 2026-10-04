package com.ivy.planned.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.domain.usecase.billreminder.BillReminderDates
import com.ivy.legacy.IvyWalletComponentPreview
import com.ivy.legacy.utils.uppercaseLocal
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.components.AddPrimaryAttributeButton

/**
 * Picks the day of the month a bill should be reminded on.
 *
 * Shown only for recurring rules. A one-time payment has a date and needs no
 * reminder, so offering a monthly day for it would be a control that cannot mean
 * anything.
 *
 * The days are a horizontal scroller rather than a numeric field, because the
 * useful range is small and fixed (1..31) and a scroller makes "the 25th" a
 * single tap instead of a keyboard entry. It also makes the month-end behaviour
 * visible: the 31st is always offered even though not every month has one, and
 * clamping happens where the date is computed rather than by hiding the option.
 * Hiding it would leave a February bill's owner with no way to express "end of
 * month".
 */
@Composable
fun BillReminderDayPicker(
    dueDayOfMonth: Int?,
    enabled: Boolean,
    onDueDayOfMonthChanged: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        if (dueDayOfMonth == null) {
            AddPrimaryAttributeButton(
                icon = R.drawable.ic_planned_payments,
                text = stringResource(R.string.bill_reminder_no_day),
                onClick = {
                    // Tapping "add" opens on the 1st rather than nothing at all:
                    // a scroller starting with nothing selected has no obvious
                    // first choice, and the 1st is the least surprising default.
                    onDueDayOfMonthChanged(BillReminderDates.MIN_DAY_OF_MONTH)
                },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.bill_reminder_remind_on, dueDayOfMonth)
                        .uppercaseLocal(),
                    style = UI.typo.b2.style(
                        fontWeight = FontWeight.ExtraBold,
                        color = UI.colors.pureInverse,
                    ),
                )

                Spacer(Modifier.weight(1f))

                // Clearing the day removes the reminder rather than parking it on
                // some default the owner never picked.
                Text(
                    text = stringResource(R.string.bill_reminder_no_day).uppercaseLocal(),
                    style = UI.typo.c.style(
                        fontWeight = FontWeight.ExtraBold,
                        color = Orange,
                    ),
                    modifier = Modifier
                        .clip(UI.shapes.r4)
                        .clickable { onDueDayOfMonthChanged(null) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            val days = (BillReminderDates.MIN_DAY_OF_MONTH..BillReminderDates.MAX_DAY_OF_MONTH)
                .toList()

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(days) { day ->
                    DayChip(
                        day = day,
                        selected = day == dueDayOfMonth,
                        onClick = { onDueDayOfMonthChanged(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    day: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Green else UI.colors.medium, CircleShape)
            .clickable(onClick = onClick)
            // 40dp: comfortably above the 48dp/5mm comfortable-target guidance is
            // not met by a dense 31-chip row, but the row scrolls, so a missed tap
            // costs one scroll rather than a mis-set reminder.
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) UI.colors.pure else UI.colors.pureInverse,
            ),
        )
    }
}

@Preview
@Composable
private fun Preview_BillReminderDay_Unset() {
    IvyWalletComponentPreview {
        BillReminderDayPicker(
            dueDayOfMonth = null,
            enabled = true,
            onDueDayOfMonthChanged = {},
        )
    }
}

@Preview
@Composable
private fun Preview_BillReminderDay_Set() {
    IvyWalletComponentPreview {
        BillReminderDayPicker(
            dueDayOfMonth = 25,
            enabled = true,
            onDueDayOfMonthChanged = {},
        )
    }
}

@Preview
@Composable
private fun Preview_BillReminderDay_OneTimeRule() {
    IvyWalletComponentPreview {
        // A one-time rule gets nothing: the control is absent, not disabled, so
        // there is no dead affordance on screen.
        BillReminderDayPicker(
            dueDayOfMonth = 25,
            enabled = false,
            onDueDayOfMonthChanged = {},
        )
    }
}
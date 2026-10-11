package com.ivy.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.ui.R
import com.ivy.legacy.utils.format

@Composable
fun IncomeExpensesRow(
    income: Double,
    expenses: Double,
    currency: String,
    modifier: Modifier = Modifier,
    textColor: Color = UI.colors.pureInverse,
    dividerColor: Color = UI.colors.medium,
    incomeLabel: String = stringResource(R.string.income_uppercase),
    expensesLabel: String = stringResource(R.string.expenses_uppercase),
    center: Boolean = true,
    dividerSpacer: Dp? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (center) {
            Spacer(Modifier.weight(1f))
        }

        LabelAmountColumn(
            textColor = textColor,
            label = incomeLabel,
            amount = income,
            currency = currency,
            center = center
        )

        if (center) {
            Spacer(Modifier.weight(1f))
        }

        if (dividerSpacer != null) {
            Spacer(modifier = Modifier.width(dividerSpacer))
        }

        // Divider
        Spacer(
            modifier = Modifier
                .width(2.dp)
                .height(48.dp)
                .background(dividerColor, UI.shapes.rFull)
        )

        if (center) {
            Spacer(Modifier.weight(1f))
        }

        if (dividerSpacer != null) {
            Spacer(modifier = Modifier.width(dividerSpacer))
        }

        LabelAmountColumn(
            textColor = textColor,
            label = expensesLabel,
            amount = expenses,
            currency = currency,
            center = center
        )

        if (center) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun LabelAmountColumn(
    label: String,
    amount: Double,
    currency: String,
    textColor: Color,
    center: Boolean
) {
    Column(
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = label,
            style = UI.typo.c.style(
                color = textColor,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Laid out here rather than delegated to AmountCurrencyB1, because making
            // the amount yield width needs RowScope and this is a Row. The summary row
            // measures its two columns against the full screen width, so with a large
            // IDR figure the right-hand column ran out of room and the currency code
            // wrapped into a vertical "ID"/"R" stack flush to the screen edge.
            // weight(fill = false) means the figure keeps its natural width unless it
            // truly cannot fit, so the common case renders exactly as before and the
            // code keeps its letters.
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = amount.format(currency),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UI.typo.nB1.style(
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = currency,
                maxLines = 1,
                style = UI.typo.nB1.style(
                    fontWeight = FontWeight.Normal,
                    color = textColor
                )
            )
        }
    }
}

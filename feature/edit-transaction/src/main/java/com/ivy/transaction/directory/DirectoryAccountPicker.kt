package com.ivy.transaction.directory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.data.db.entity.DirectoryAccountEntity
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.modal.IvyModal

/**
 * Picks the destination account on another profile for a cross-profile transfer.
 *
 * The owner records these transfers after the fact, so this labels something that
 * already happened rather than requesting anything. Nothing waits for the other
 * person to accept.
 *
 * The empty state names the cause rather than showing a blank list. An empty
 * directory means this device has not synced with the other phone yet; a bare
 * "no accounts" would read as "she has no accounts", which is a different and
 * wrong conclusion.
 */
@Composable
fun BoxScope.DirectoryAccountPicker(
    visible: Boolean,
    accounts: List<DirectoryAccountEntity>,
    onSelect: (DirectoryAccountEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    IvyModal(
        id = null,
        visible = visible,
        dismiss = onDismiss,
        shiftIfKeyboardShown = false,
        PrimaryAction = { Spacer(Modifier.height(1.dp)) },
    ) {
        Text(
            modifier = Modifier.padding(start = 32.dp, end = 32.dp),
            text = stringResource(R.string.send_to),
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            modifier = Modifier.padding(start = 32.dp, end = 32.dp),
            text = stringResource(R.string.send_to_description),
            style = UI.typo.c.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Medium,
            ),
        )

        Spacer(Modifier.height(24.dp))

        if (accounts.isEmpty()) {
            EmptyDirectory()
        } else {
            // A bounded height, and the reason is not cosmetic.
            //
            // IvyModal puts its content in a verticallyScrollable Column and this
            // file used to add a `Spacer(Modifier.height(400.dp))` "scroll hack"
            // after the list to fake bottom padding. Inside a scrollable parent the
            // child's height constraint is infinite, and a LazyColumn measured with
            // an infinite maximum height throws:
            //
            //   IllegalStateException: Vertically scrollable component was
            //   measured with an infinity maximum height constraints
            //
            // That killed the whole process the instant the picker opened, which is
            // why opening it silently closed the app. Two nested vertical
            // scrollers is the actual mistake: the modal scrolls, so the list
            // inside it must not also want to scroll without a bound.
            //
            // So: a real, finite height, and no fake spacer. The two spacer lines
            // that used to follow the list in this file were removed deliberately.
            LazyColumn(
                modifier = Modifier.height(320.dp),
            ) {
                items(accounts, key = { it.accountId.toString() }) { entry ->
                    DirectoryAccountRow(entry = entry, onClick = { onSelect(entry) })
                }
            }
        }
    }
}

@Composable
private fun EmptyDirectory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.no_other_accounts_yet),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        Text(
            text = stringResource(R.string.no_other_accounts_yet_description),
            style = UI.typo.c.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun DirectoryAccountRow(
    entry: DirectoryAccountEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 48dp keeps the row at the platform minimum touch target height.
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = entry.profileName,
                style = UI.typo.c.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        entry.currencyCode?.let { code ->
            Text(
                text = code,
                style = UI.typo.c.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

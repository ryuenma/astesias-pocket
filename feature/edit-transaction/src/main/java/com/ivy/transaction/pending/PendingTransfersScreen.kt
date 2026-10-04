package com.ivy.transaction.pending

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.db.entity.PendingTransferEntity
import com.ivy.domain.usecase.transfer.PendingTransferRepository
import com.ivy.domain.usecase.transfer.TransferInbox
import com.ivy.navigation.PendingTransfersScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PendingTransfersViewModel @Inject constructor(
    private val repository: PendingTransferRepository,
) : ViewModel() {

    private val pending = mutableStateOf<ImmutableList<PendingTransferEntity>>(
        persistentListOf()
    )

    val items: ImmutableList<PendingTransferEntity> get() = pending.value

    /**
     * Re-reads the inbox on entry, so the count Settings shows and the rows here
     * agree with what is actually in the table.
     *
     * Never throws: sync is a background nicety, and an inbox that failed to read
     * must still open showing what was last known rather than dead-ending the
     * user on the screen she navigated to deliberately.
     */
    fun load() {
        viewModelScope.launch {
            pending.value = runCatching {
                repository.pending().toImmutableList()
            }.getOrElse { error ->
                Timber.e(error, "Could not load pending transfers")
                pending.value
            }
        }
    }

    /**
     * Accept writes a real TRANSFER row into her ledger; decline writes only a
     * tombstone and leaves the ledger untouched. Both re-read the inbox
     * afterwards rather than removing the row optimistically, so the list can
     * never claim a decision the database refused.
     */
    private fun decide(
        entry: PendingTransferEntity,
        action: TransferInbox.Action,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.decide(
                    correlationId = entry.correlationId,
                    action = action,
                    // Unused on the decline path: declining never writes a ledger
                    // row, so this id is never consulted.
                    confirmedLegId = UUID.randomUUID(),
                    // Derived from the correlation id rather than randomised, so
                    // a retry names the same counterparty account instead of
                    // inventing a second identity for the same transfer.
                    senderAccountId = UUID.nameUUIDFromBytes(
                        "pending-transfer-sender:${entry.correlationId}".toByteArray()
                    ),
                )
            }.onFailure {
                Timber.e(it, "Could not apply $action to transfer ${entry.correlationId}")
            }
            pending.value = repository.pending().toImmutableList()
        }
    }

    fun accept(entry: PendingTransferEntity) {
        decide(entry, TransferInbox.Action.ACCEPT)
    }

    fun decline(entry: PendingTransferEntity) {
        decide(entry, TransferInbox.Action.DECLINE)
    }
}

/**
 * The review surface for incoming cross-profile transfers.
 *
 * This exists because the inbox used to be rendered only from inside the
 * transaction form, behind a flag nothing ever set: the partner's transfer
 * arrived in `pending_transfers` and there was no affordance anywhere to accept
 * or decline it. Confirming money is not a step in recording a transaction -
 * someone who never records a transfer still has to be able to accept one - so
 * it gets its own destination, reachable from Settings.
 */
@Composable
fun BoxWithConstraintsScope.PendingTransfersScreen(screen: PendingTransfersScreen) {
    val viewModel: PendingTransfersViewModel = screenScopedViewModel()
    // Read here, not inside the lambda: navigation() is @Composable, and a
    // non-composable () -> Unit argument has no Compose context of its own.
    val nav = navigation()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pending_transfers_screen")
    ) {
        PendingTransfersModal(
            visible = true,
            items = viewModel.items,
            onAccept = { viewModel.accept(it) },
            onDecline = { viewModel.decline(it) },
            // Done, the close control and the system back gesture all mean the
            // same thing here: she has finished looking at the inbox.
            onDismiss = { nav.back() },
        )
    }
}
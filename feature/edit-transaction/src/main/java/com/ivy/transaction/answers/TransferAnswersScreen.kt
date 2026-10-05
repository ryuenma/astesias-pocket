package com.ivy.transaction.answers

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
import com.ivy.data.db.entity.TransferConfirmationWithEvent
import com.ivy.data.sync.TransferOutboxRepository
import com.ivy.domain.usecase.transfer.TransferConfirmationRepository
import com.ivy.domain.usecase.transfer.TransferEventSync
import com.ivy.navigation.TransferAnswersScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads and publishes, so the answers on screen are not stale.
 *
 * The ORDER is deliberate and it is the same one
 * [com.ivy.transaction.pending.PendingTransfersViewModel] uses: publish what
 * this device owes the server first, so her own answer has actually left this
 * phone before anything is claimed about it; then pull, so the list is built from
 * what the server said just now rather than from what was there on entry; then
 * read.
 *
 * [TransferConfirmationRepository.pullAnswers] persists, so this screen is not
 * showing the envelope the pull happened to return - it is showing the durable
 * table. That is what makes the list survive the screen being closed.
 */
@HiltViewModel
class TransferAnswersViewModel @Inject constructor(
    private val repository: TransferConfirmationRepository,
    private val eventSync: TransferEventSync,
    private val outbox: TransferOutboxRepository,
) : ViewModel() {

    private val items = mutableStateOf<ImmutableList<TransferConfirmationWithEvent>>(
        persistentListOf()
    )

    val answers: ImmutableList<TransferConfirmationWithEvent> get() = items.value

    /**
     * Refreshes the list and then marks it read.
     *
     * Acknowledging AFTER the read, and only once the refresh has finished, is
     * what stops the badge being a lie: a row can only be "seen" if it was
     * actually on screen. Acknowledging first would clear the count on an entry
     * that then failed to load, and the owner would never be told again.
     *
     * Never throws. Sync is a background nicety, and a failed refresh must still
     * leave a readable screen showing the last known answers rather than a dead
     * end on the destination she navigated to deliberately.
     */
    fun load() {
        viewModelScope.launch {
            // Her side of the same conversation: whatever she still owes the
            // server goes first, so nothing here is claimed about an answer that
            // has not left this device.
            runCatching { outbox.drain() }
                .onFailure { Timber.e(it, "Could not drain the transfer outbox") }

            runCatching { eventSync.pullConfirmations() }
                .onFailure { Timber.e(it, "Could not pull transfer confirmations") }

            val loaded = runCatching {
                repository.answered().toImmutableList()
            }.getOrElse { error ->
                Timber.e(error, "Could not load recorded transfer answers")
                items.value
            }
            items.value = loaded

            // Only once the rows are on screen. See the function doc.
            runCatching { repository.acknowledgeAll() }
                .onFailure { Timber.e(it, "Could not acknowledge transfer answers") }
        }
    }
}

/**
 * What the counterpart did about the money this device sent.
 *
 * This is the owner's counterpart to the pending-transfers inbox, and it exists
 * because that inbox answered only half the question. Someone who sends a
 * cross-profile transfer and hears nothing has no way to tell "still waiting"
 * from "declined and nobody told me", so the answer is fetched, stored and shown
 * here - reachable from Settings, where the badge counts what she has not read.
 *
 * Deliberately a destination rather than a transient message. A snackbar fired by
 * a background sync is seen by nobody at all unless she happens to be looking at
 * the screen at that moment, and the whole point is that she finds out.
 */
@Composable
// `screen` is unused because this destination carries no arguments, and the
// parameter is kept only so the signature matches the navigation dispatch that
// calls it - PendingTransfersScreen is in the same position. ModifierMissing and
// the Modal's stability findings are the same ones PendingTransfersModal
// already carries in full.
@Suppress("UnusedParameter", "ModifierMissing")
fun BoxWithConstraintsScope.TransferAnswersScreen(screen: TransferAnswersScreen) {
    val viewModel: TransferAnswersViewModel = screenScopedViewModel()
    // Read here, not inside the lambda: navigation() is @Composable, and a
    // non-composable () -> Unit argument has no Compose context of its own.
    val nav = navigation()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transfer_answers_screen")
    ) {
        TransferAnswersModal(
            visible = true,
            answers = viewModel.answers,
            // Done, the close control and the system back gesture all mean the
            // same thing here: she has finished looking.
            onDismiss = { nav.back() },
        )
    }
}

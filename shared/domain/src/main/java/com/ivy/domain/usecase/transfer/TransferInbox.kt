package com.ivy.domain.usecase.transfer

/**
 * The one decision that keeps sync safe: what should happen when a transfer
 * event arrives for a correlation id we may already have seen?
 *
 * A pure function so the rule is decided in one testable place rather than being
 * re-derived across a repository and a DAO. It is only half the guarantee; the
 * inbox table's primary key is the other half, and only the pair is sufficient -
 * see `Migration132to133_PendingTransfers`.
 */
object TransferInbox {

    /**
     * What an arriving event does to a transfer we may already know about.
     */
    sealed interface Outcome {
        /** We had never seen this correlation id. Record it as PENDING. */
        data class Create(val state: PendingTransferState) : Outcome

        /** Already known and still awaiting a decision. The replay changes nothing. */
        data object Ignore : Outcome

        /** Already ACCEPTED. The replay changes nothing - the ledger row stands. */
        data object AlreadyAccepted : Outcome

        /** Already DECLINED. The replay changes nothing - it does NOT resurrect. */
        data object AlreadyDeclined : Outcome
    }

    /**
     * Decides what an arriving event does.
     *
     * [existing] is null when the correlation id is new to this device, which is
     * the normal first delivery.
     *
     * Every non-null case returns something that writes nothing. That is the
     * whole point: the only path that creates a row is the one where we are
     * certain the row does not exist. A replay can never reach it, because the
     * correlation id is the idempotency key and it is already present.
     */
    fun onEventArrived(existing: PendingTransferState?): Outcome = when (existing) {
        null -> Outcome.Create(PendingTransferState.PENDING)
        PendingTransferState.PENDING -> Outcome.Ignore
        PendingTransferState.ACCEPTED -> Outcome.AlreadyAccepted
        PendingTransferState.DECLINED -> Outcome.AlreadyDeclined
    }

    /**
     * The result of the counterpart acting on a pending item.
     *
     * [PENDING -> ACCEPTED] is the only path that creates a ledger row, and it
     * is guarded twice: the state must be PENDING, and the ledger insert must be
     * keyed on the same correlation id.
     */
    sealed interface Decision {
        /** She confirmed it. Materialise the real TRANSFER row. */
        data object Accept : Decision

        /** She declined it. Tombstone it; her ledger stays untouched. */
        data object Decline : Decision

        /**
         * The action does not apply - already decided, or already gone.
         *
         * Reached by a double-tap, which on a phone is entirely routine: two
         * taps, two coroutines, one intent. The second must be a no-op rather
         * than a second ledger row.
         */
        data object NoChange : Decision
    }

    /**
     * Decides what tapping accept or decline does to a pending item.
     *
     * [existing] is null when the item is already gone from the inbox, which is
     * what a replayed confirm looks like after the first one committed.
     */
    fun onUserDecision(existing: PendingTransferState?, action: Action): Decision = when {
        // Already gone, or never was. Nothing to decide.
        existing == null -> Decision.NoChange

        // Already decided. A double-tap lands here and changes nothing.
        existing.isTerminal -> Decision.NoChange

        action == Action.ACCEPT -> Decision.Accept
        else -> Decision.Decline
    }

    /** What the counterpart tapped. */
    enum class Action { ACCEPT, DECLINE }
}

/**
 * Whether this outcome is allowed to write to the database.
 *
 * Kept separate from [TransferInbox.Outcome] so the write site has to ask the
 * question explicitly. A repository that pattern-matches on the outcome and
 * inserts in every branch has a bug waiting to happen.
 */
fun TransferInbox.Outcome.writesNewRow(): Boolean = this is TransferInbox.Outcome.Create

/**
 * Whether this decision changes the ledger.
 *
 * Only ACCEPT does. Declining is deliberately ledger-neutral: the money already
 * moved, her declining it cannot un-move it, and the owner's row stays exactly
 * as recorded. Retracting the owner's transfer because the recipient did not want
 * it would falsify the owner's ledger - the single worst thing this feature
 * could do.
 */
fun TransferInbox.Decision.touchesLedger(): Boolean = this == TransferInbox.Decision.Accept
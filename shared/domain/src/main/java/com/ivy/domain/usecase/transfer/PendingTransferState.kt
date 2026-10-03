package com.ivy.domain.usecase.transfer

/**
 * Where an incoming cross-profile transfer has got to on the COUNTERPART's side.
 *
 * The owner's side has no state machine at all, and deliberately so: she records
 * the transfer the instant the money moves, and that row is never retracted by
 * anything the counterpart does. Only the mirrored leg has to wait for a human.
 */
enum class PendingTransferState {
    /**
     * Arrived, waiting for the counterpart to tap accept.
     *
     * Not yet in her ledger. Not income, not a transfer - simply not decided.
     */
    PENDING,

    /**
     * She confirmed it. A real TRANSFER row now exists in her ledger.
     */
    ACCEPTED,

    /**
     * She declined it. She will not adopt this money into her wallet.
     *
     * This is a RETAINED state rather than a deleted row, and the reason is
     * idempotency. If declining deleted the row, the next replayed POST of the
     * same transfer would find no row, insert a fresh PENDING one, and the
     * transfer she just dismissed would come back from the dead - on every sync,
     * forever. Keeping a tombstone keyed by the correlation id is what makes a
     * replay of a declined transfer a no-op instead of a resurrection.
     */
    DECLINED,
    ;

    /**
     * Whether a transfer in this state still owes the user a decision.
     *
     * Used to decide what a freshly-arrived event is allowed to do: it may only
     * move a row to PENDING if the row is genuinely new, or resurrect one that
     * was accepted and then... no. It may never resurrect anything. Accepted and
     * declined are both terminal, so an arriving event for either is a replay.
     */
    val isTerminal: Boolean
        get() = this != PENDING
}
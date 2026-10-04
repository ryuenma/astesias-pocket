package com.ivy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A cross-profile transfer waiting on the counterpart to confirm it.
 *
 * This is NOT a transaction. It is a claim that money is on its way, held
 * outside the ledger until a human accepts it. Keeping it in its own table is
 * what makes "visibly pending, not silently absent and not a normal TRANSFER"
 * structural rather than a convention: every balance, report, export and CSV
 * reads `transactions`, and nothing in this table is ever in it, so an unconfirmed
 * item cannot be counted, totalled or exported by accident.
 *
 * The owner's leg is deliberately absent here. She records her side immediately
 * and unconditionally; this table only ever describes what HER device is waiting
 * to be told about.
 *
 * [correlationId] is the primary key, and that is the load-bearing decision of
 * the whole feature rather than an incidental choice: it is both the
 * cross-profile join key, because the two legs share no account id, and the
 * idempotency key. See `Migration132to133_PendingTransfers` for why the database
 * has to enforce that rather than application logic.
 *
 * A declined transfer is RETAINED as [state] = DECLINED rather than deleted, for
 * the same reason. Deleting it would let the next replayed delivery insert a
 * fresh PENDING row, and the transfer she dismissed would return on every sync
 * thereafter, with nothing left to say she had already said no.
 *
 * Deliberately NOT `@Serializable`: like [DirectoryAccountEntity] this is a
 * sync-derived local cache and never part of the user's Ivy backup export.
 */
@Suppress("DataClassDefaultValues")
@Entity(tableName = "pending_transfers")
data class PendingTransferEntity(
    /**
     * Correlation id, and the primary key. See the class doc for why.
     */
    @PrimaryKey
    val correlationId: UUID,

    /** Profile that recorded the transfer - the counterparty, from her side. */
    val recordedByProfileId: String,

    /** Display name of that profile, so the item reads before sync completes. */
    val recordedByProfileName: String,

    /** Her account the money arrived into. Local to her; resolvable in her ledger. */
    val intoAccountId: UUID,

    /**
     * Name of the account it left on his side.
     *
     * A name and not an id, on purpose: his account does not exist in her
     * database, so an id would be an unresolvable foreign key. This is the same
     * reasoning as the server's `fromAccountName` field.
     */
    val fromAccountName: String,

    val amount: Double,

    /** Currency of [intoAccountId], so the amount is not displayed unqualified. */
    val currency: String,

    /** ISO date, no time - matching what the owner actually enters. */
    val occurredOn: Instant,

    val title: String? = null,
    val description: String? = null,

    /**
     * PENDING until she decides; ACCEPTED or DECLINED forever after.
     *
     * Terminal states are never overwritten by a later delivery - see
     * `com.ivy.domain.usecase.transfer.TransferInbox`.
     */
    val state: String,
)
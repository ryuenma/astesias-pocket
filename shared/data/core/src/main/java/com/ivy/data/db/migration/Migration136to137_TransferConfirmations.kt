package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The counterpart's answers about transfers this device sent.
 *
 * ## What this migration does NOT do, which is the whole design
 *
 * It creates one new table and touches nothing else. No `transactions` row is
 * read or written, no account is renamed, and the owner's recorded transfers -
 * which are the actual money - are not so much as inspected. A device that has
 * never recorded a cross-profile transfer ends up with an empty answer table,
 * which is the correct state rather than a fabricated one.
 *
 * There is no DROP and no table recreation anywhere in here. That is not
 * stylistic: every reader of `transactions` in this codebase is mechanical
 * (`TransactionDao.findAll()` has no filter and feeds the balance use cases and
 * both exports), so a migration that rebuilt any table containing data would
 * risk losing rows that no single query could be shown to have been at risk.
 *
 * ## Why a separate table and not a column on `transfer_outbox`
 *
 * The outbox is a queue: what this device still owes the server, cleared to SENT
 * once published. An answer arrives afterwards, outlives the queue, and has to
 * survive both a drain and a re-sync. Repurposing a tombstone row to also mean
 * "she answered" would give one row two unrelated claims, and the `deleteAll()`
 * that runs on sign-out would take the owner's record of what happened to her
 * money along with the transport state.
 *
 * Same reasoning that put `pending_transfers` in its own table (see
 * `Migration132to133_PendingTransfers`) and `pending_bills` in its own (see
 * `Migration134to135_PendingBills`).
 *
 * ## `correlationId` is the PRIMARY KEY, and that is what makes a pull safe
 *
 * The id is both the cross-profile join key and the idempotency key. Every
 * profile may read every confirmation, so this device necessarily sees the
 * answer the OWNER published herself coming back in the same feed, and a sync
 * that runs twice delivers the same answer twice. The key means the second
 * delivery collides here and is refused.
 *
 * It is IGNORE and not REPLACE that makes the refusal safe: REPLACE would write
 * the default `acknowledged = 0` over a row the owner had already read, so the
 * badge would light up again on every sync and she could never reach a state
 * where she had seen everything.
 *
 * ## No index beyond the primary key
 *
 * The queries are the primary-key lookup, a full scan bounded by one row per
 * transfer this device has ever sent, and a `COUNT` over that same tiny set. An
 * index would cost a write per answer and buy nothing measurable.
 *
 * @see com.ivy.data.db.entity.TransferConfirmationEntity
 */
// ClassNaming and MagicNumber are suppressed rather than baselined, because the
// name is the convention: `Migration<from>to<to>_<Feature>` is what every other
// migration in this package is called and what a reader greps for. The two
// magic numbers ARE the migration's identity - changing either changes which
// installs can take this path - so naming them in a constant would obscure
// rather than clarify.
@Suppress("ClassNaming", "MagicNumber")
class Migration136to137_TransferConfirmations : Migration(136, 137) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transfer_confirmations` (
                `correlationId` TEXT NOT NULL,
                `decision` TEXT NOT NULL,
                `decidedBy` TEXT NOT NULL,
                `recordedAt` INTEGER NOT NULL,
                `acknowledged` INTEGER NOT NULL,
                PRIMARY KEY(`correlationId`)
            )
            """.trimIndent()
        )
    }
}

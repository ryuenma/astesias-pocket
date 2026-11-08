package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Carries the destination account's directory id on the transfer outbox.
 *
 * Adds one optional column, `toAccountId`, to `transfer_outbox`. An `ALTER
 * TABLE ... ADD COLUMN` only: every existing row stays untouched, and its new
 * column reads NULL, which is exactly the meaning "recorded before ids were
 * carried; place me by name" - no data rewrite, no table rebuild.
 *
 * ## Why the queue carries an id at all, when the wire used to be names
 *
 * A cross-profile transfer names its destination by what the SENDER picked
 * from the shared account directory. The wire used to carry `toAccountName`,
 * and the receiver matched it against her own accounts by exact name - so a
 * transfer could not be placed whenever the sender's view of the account name
 * had drifted from the receiver's actual name (a rename on one phone, a
 * republish that loaded a stale name, two devices set up from different
 * backups). The name you refer by and the name the data answers to are two
 * different facts, and matching couples them.
 *
 * `toAccountId` decouples them: it is the directory row's `accountId`, the
 * key both devices already agree on because the directory is where the
 * sender's picker got the destination in the first place. The receiver
 * resolves the id through her own directory cache and then to a live local
 * account - see `TransferEventSync.deliver`, which matches by this id when
 * present and by name only for rows that predate it.
 *
 * The wire field is optional, so this client still publishes and still
 * accepts rows that carry only a name: old rows keep flowing, and the
 * name-based match stays the fallback rather than a removed path.
 *
 * @see com.ivy.data.db.entity.TransferOutboxEntity
 */
class Migration138to139_TransferOutboxToAccountId : Migration(138, 139) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transfer_outbox` ADD COLUMN `toAccountId` TEXT")
    }
}

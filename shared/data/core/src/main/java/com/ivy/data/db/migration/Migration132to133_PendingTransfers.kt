package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the counterpart's confirmation inbox.
 *
 * A brand-new table, so nothing existing is touched: no `transactions` row is
 * altered, and a user who has never received a partner transfer gets an empty
 * inbox rather than a fabricated one.
 *
 * The table is separate from `transactions` on purpose. Every balance, report,
 * CSV export and XLSX export reads `transactions`, and a pending item that
 * lived there would eventually be counted by one of them that nobody remembered
 * to filter. Keeping unconfirmed money in its own table makes that impossible
 * rather than a rule to remember.
 *
 * `correlationId` is the PRIMARY KEY, and that is the load-bearing part of the
 * migration rather than a detail. It is simultaneously the cross-profile join
 * key and the idempotency key: two deliveries of one transfer event resolve to
 * the same row identity, so the second INSERT is refused by the database instead
 * of creating a second pending item. Application-level checks alone cannot
 * close the window between two in-flight deliveries, because both would read
 * "not seen yet" before either commits.
 *
 * This is the property that has already broken once on this project: a
 * PocketBase collection declared `unique: true` on a field with no real index
 * behind it, so a replayed POST returned 200 and the same transfer was recorded
 * twice. A real PRIMARY KEY is the difference between impossible and unlikely.
 *
 * No index on `state` beyond the primary key. The only query that filters by
 * state is the pending list, which is bounded by the size of one person's
 * in-flight transfers (single digits), so an index would cost writes on every
 * sync for no measurable read win.
 *
 * @see com.ivy.data.db.entity.PendingTransferEntity
 */
class Migration132to133_PendingTransfers : Migration(132, 133) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_transfers` (
                `correlationId` TEXT NOT NULL,
                `recordedByProfileId` TEXT NOT NULL,
                `recordedByProfileName` TEXT NOT NULL,
                `intoAccountId` TEXT NOT NULL,
                `fromAccountName` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `currency` TEXT NOT NULL,
                `occurredOn` INTEGER NOT NULL,
                `title` TEXT,
                `description` TEXT,
                `state` TEXT NOT NULL,
                PRIMARY KEY(`correlationId`)
            )
            """.trimIndent()
        )
    }
}
package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Gives `directory_accounts` the identity the server already had: the composite
 * `(profileId, accountId)`.
 *
 * ## The defect this closes, measured
 *
 * The table declared `accountId` as its sole primary key, while the server's
 * uniqueness is the composite `(profileId, localAccountId)`. A directory refresh
 * fetches every profile's rows and inserts them with
 * `OnConflictStrategy.REPLACE`, so any account id published by two profiles
 * collapsed to one row and the later profile silently overwrote the earlier.
 *
 * On a device paired as `qa_a` whose server held 30 rows (`qa_a` 21 + `qa_b` 9,
 * with 9 ids shared), one refresh left the table holding 21 rows - all `qa_a`,
 * zero `qa_b`. `findOthers("qa_a")` then returned nothing and the destination
 * picker read "No other profiles yet" for ever, on a healthy sync token with a
 * fully populated table.
 *
 * ## Why the table is rebuilt rather than altered
 *
 * SQLite cannot change a primary key in place; `ALTER TABLE` has no such form.
 * So this follows Room's own four-step rebuild: create the new shape, copy,
 * drop, rename.
 *
 * ## No row can be lost by the rebuild
 *
 * The old key made `accountId` unique across the WHOLE table, so every surviving
 * row already has a distinct `(profileId, accountId)` pair - the new key cannot
 * refuse any of them. The rebuild is a straight copy.
 *
 * What it cannot do is bring back the rows that had already been overwritten:
 * those were gone the moment the REPLACE happened, and no schema change can
 * recover data that was never stored. The next refresh re-fetches both profiles
 * from the server and this time both sets land. That is why the fix has to be a
 * schema change and not a query change.
 *
 * ## The old `profileId` index is deliberately not recreated
 *
 * `profileId` now LEADS the primary key, so it is already indexed and
 * `findOthers`/`findByProfile` are served by the primary key index. Dropping the
 * table drops `index_directory_accounts_profileId` with it, which is the point:
 * keeping it would mean two index writes per insert for one index too many.
 *
 * ## Nothing else in the database is touched
 *
 * No `transactions` row is read or written. This table is a sync-derived cache:
 * it holds no balances and no ledger rows, so a rebuild here cannot cost the
 * owner any money record. `accounts`, `transactions` and every other table are
 * not so much as named.
 *
 * @see com.ivy.data.db.entity.DirectoryAccountEntity
 */
@Suppress("ClassNaming", "MagicNumber")
class Migration137to138_DirectoryAccountProfileKey : Migration(137, 138) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `directory_accounts_new` (
                `profileId` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `profileName` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `currencyCode` TEXT,
                PRIMARY KEY(`profileId`, `accountId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `directory_accounts_new`
                (`profileId`, `accountId`, `profileName`, `name`, `currencyCode`)
            SELECT `profileId`, `accountId`, `profileName`, `name`, `currencyCode`
            FROM `directory_accounts`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `directory_accounts`")
        db.execSQL("ALTER TABLE `directory_accounts_new` RENAME TO `directory_accounts`")
    }
}

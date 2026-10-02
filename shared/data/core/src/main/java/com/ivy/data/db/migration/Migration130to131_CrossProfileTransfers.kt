package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds cross-profile transfer support.
 *
 * Both transaction columns are nullable, so every existing row - including the
 * 181 intra-profile transfers in a real backup - keeps its meaning: a null
 * counterpart means "nobody else's money involved".
 *
 * The directory table is created empty rather than back-filled. Account UUIDs are
 * generated on the owner's device and are only meaningful there, so there is
 * nothing to migrate.
 */
class Migration130to131_CrossProfileTransfers : Migration(130, 131) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN counterpartProfileId TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN linkedTransactionId TEXT")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `directory_accounts` (
                `accountId` TEXT NOT NULL,
                `profileId` TEXT NOT NULL,
                `profileName` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `currencyCode` TEXT,
                PRIMARY KEY(`accountId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_directory_accounts_profileId` " +
                "ON `directory_accounts` (`profileId`)"
        )
    }
}

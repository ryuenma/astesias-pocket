package com.ivy.data.db.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * Proves `Migration137to138_DirectoryAccountProfileKey` rebuilds
 * `directory_accounts` on the composite key the server already had.
 *
 * ## Why this is not a `MigrationTestHelper` test
 *
 * Because `room.testing` is `androidTestImplementation` only, and a test nobody
 * runs proves nothing - see the note on `Migration136to137_TransferConfirmationsTest`.
 * The engine-level proof of this exact migration lives in
 * `shared/data/core/src/androidTest/.../Migration137to138Test.kt`, which runs
 * `runMigrationsAndValidate` against real SQLite on a device and is the test that
 * catches a drifted `createSql`.
 *
 * What is checkable on the JVM, and is checked here, is the destructive half:
 * that the rebuild COPIES before it drops, and that it never touches a table
 * holding the owner's money.
 *
 * The row-count regression itself - two profiles sharing one account id both
 * surviving a refresh - is proven in
 * `com.ivy.data.sync.DirectorySyncRepositoryTest`, which runs the real refresh
 * against a fake DAO that enforces SQLite's key semantics.
 */
@Suppress("ClassNaming")
class Migration137to138_DirectoryAccountProfileKeyTest {

    private val db: SupportSQLiteDatabase = mockk(relaxed = true)

    private fun sql(): List<String> {
        val captured = mutableListOf<String>()
        every { db.execSQL(capture(captured), any<Array<Any>>()) } answers { Unit }
        every { db.execSQL(capture(captured)) } answers { Unit }
        Migration137to138_DirectoryAccountProfileKey().migrate(db)
        return captured
    }

    private fun normalized(): List<String> =
        sql().map { it.replace(Regex("\\s+"), " ").trim() }

    @Test
    fun `it migrates from 137 to 138`() {
        // Room picks a migration by matching start and end against the installed
        // and requested versions. A wrong number means an install on 137 has no
        // path forward and throws instead of migrating.
        val migration = Migration137to138_DirectoryAccountProfileKey()

        migration.startVersion shouldBe 137
        migration.endVersion shouldBe 138
    }

    @Test
    fun `the new table is keyed on profileId and accountId together`() {
        // THE fix. While `accountId` was the only key, a refresh that fetched
        // both profiles' rows could hold a shared account id ONCE, and REPLACE
        // silently overwrote whichever profile came second.
        val create = normalized().single { it.startsWith("CREATE TABLE") }

        create.contains("PRIMARY KEY(`profileId`, `accountId`)") shouldBe true
        // Belt and braces: the single-column form is exactly the bug, so its
        // presence in any statement is a failure, not a style preference.
        normalized().none { it.contains("PRIMARY KEY(`accountId`)") } shouldBe true
    }

    @Test
    fun `it copies the old rows into the new table before dropping anything`() {
        // The order is the whole safety of a table rebuild. Copy-then-drop keeps
        // every row; drop-then-copy keeps none.
        val statements = normalized()
        val copyAt = statements.indexOfFirst { it.startsWith("INSERT INTO `directory_accounts_new`") }
        val dropAt = statements.indexOfFirst { it.startsWith("DROP TABLE") }

        (copyAt >= 0) shouldBe true
        (dropAt >= 0) shouldBe true
        (copyAt < dropAt) shouldBe true
    }

    @Test
    fun `the copy carries every column, so no row loses a field`() {
        // A copy that omitted `currencyCode` or `profileName` would blank those
        // fields on every device that upgrades, and the picker would render
        // accounts with no owner name and no currency chip.
        val copy = normalized().single { it.startsWith("INSERT INTO `directory_accounts_new`") }

        listOf("profileId", "accountId", "profileName", "name", "currencyCode").forEach { column ->
            copy.contains("`$column`") shouldBe true
        }
    }

    @Test
    fun `it drops the old table and renames the new one into its place`() {
        // Without both halves the app ends up either with two tables or with
        // none, and Room's schema validation rejects the result either way.
        val statements = normalized()

        statements.single { it.startsWith("DROP TABLE") }
            .contains("DROP TABLE `directory_accounts`") shouldBe true
        statements.single { it.startsWith("ALTER TABLE") } shouldBe
            "ALTER TABLE `directory_accounts_new` RENAME TO `directory_accounts`"
    }

    @Test
    fun `it does not recreate the old profileId index`() {
        // `profileId` now LEADS the primary key, so it is already indexed and
        // findOthers/findByProfile are served by the primary key index. A second
        // index on the same column would cost an extra write per insert.
        normalized().none { it.contains("index_directory_accounts_profileId") } shouldBe true
    }

    @Test
    fun `it never mentions a table that holds the owner's money`() {
        // This table is a sync-derived cache of ids, names and currencies. No
        // balance and no ledger row ever crosses profiles, so nothing outside
        // `directory_accounts` may be read, written or dropped by this migration.
        //
        // The table's own name is blanked out BEFORE the scan, not excluded from
        // the list. `directory_accounts` contains the substring `accounts`, so a
        // naive scan reports the migration as touching the owner's account list
        // no matter what it does - a check that can only ever fail is worse than
        // no check, because it teaches the reader to ignore it.
        val statements = sql()
            .joinToString("\n")
            .replace("directory_accounts", "<this table>")
            .lowercase()

        listOf("transactions", "accounts", "transfer_outbox", "pending_transfers")
            .forEach { table ->
                statements.contains(table) shouldBe false
            }
    }
}

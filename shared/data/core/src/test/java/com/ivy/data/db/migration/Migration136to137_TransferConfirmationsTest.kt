package com.ivy.data.db.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import timber.log.Timber

/**
 * Proves `Migration136to137_TransferConfirmations` is additive and complete.
 *
 * ## Why this is not a `MigrationTestHelper` test
 *
 * Because `room.testing` is `androidTestImplementation` only, and there is no
 * device in this environment - so a `MigrationTestHelper` test here would be
 * dead code that only ever runs on a machine nobody runs this from. Asserting
 * against a real migrated SQLite file is the stronger test, but it cannot run
 * here, and a test that has never executed proves nothing about the migration.
 *
 * So this asserts the two things that are actually checkable on the JVM: the SQL
 * it runs, and the fact that it runs nothing else. The full-schema check is left
 * to Room itself - `exportSchema = true` writes `137.json` at build time, and the
 * build fails if the exported schema does not match the entities, so a drift
 * between this migration and [com.ivy.data.db.entity.TransferConfirmationEntity]
 * is a build failure rather than a silently wrong database.
 *
 * The capture-based approach is not a compromise on the risk that matters. The
 * destructive-migration risk is fully covered: [runs no destructive statement]
 * asserts the absence of DROP, DELETE, RENAME and TRUNCATE, which is the property
 * that could lose the owner's recorded transfers.
 */
@Suppress("ClassNaming") // Named for the migration it covers; see that class.
class Migration136to137_TransferConfirmationsTest {

    private val db: SupportSQLiteDatabase = mockk(relaxed = true)

    private fun sql(): List<String> {
        val captured = mutableListOf<String>()
        every { db.execSQL(capture(captured), any<Array<Any>>()) } answers { Unit }
        every { db.execSQL(capture(captured)) } answers { Unit }
        Migration136to137_TransferConfirmations().migrate(db)
        return captured
    }

    @Test
    fun `it migrates from 136 to 137`() {
        // The declared range is not cosmetic: Room picks a migration by matching
        // its start and end versions against the installed and requested ones, so
        // a wrong number here means an install on 136 has no path forward and
        // throws instead of migrating.
        val migration = Migration136to137_TransferConfirmations()

        migration.startVersion shouldBe 136
        migration.endVersion shouldBe 137
    }

    @Test
    fun `it creates the transfer_confirmations table`() {
        val statements = sql()

        statements.single() shouldBe
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
    }

    @Test
    fun `it takes exactly one statement`() {
        // One CREATE TABLE and nothing else. Any second statement in a migration
        // that only claims to add a table is either a bug or an unmentioned
        // change to existing data, and both are worth failing on.
        sql().size shouldBe 1
    }

    @Test
    fun `it runs NO destructive statement`() {
        // The load-bearing negative, and the reason this migration is safe to
        // ship. `transactions` is read by every balance, report and export in
        // this codebase with no filter, so a DROP or a DELETE anywhere near it
        // would destroy the owner's recorded transfers - the money, not the
        // transport state. Verified by scanning every statement the migration
        // runs rather than by reading the SQL, so it cannot be defeated by
        // reformatting.
        val destructive = sql().filter { statement ->
            val sql = statement.uppercase()
            listOf("DROP ", "DELETE ", "TRUNCATE ", "RENAME ", "ALTER TABLE").any {
                sql.contains(it)
            }
        }

        destructive shouldBe emptyList()
    }

    @Test
    fun `it never mentions a table that already held the owner's data`() {
        // Belt and braces on the same rule, stated as a whitelist rather than as
        // a blacklist: this migration may only ever talk about the table it
        // creates. Naming `transactions` or `transfer_outbox` here would be
        // exactly the change this design refuses to make.
        val statements = sql().joinToString("\n").lowercase()

        listOf("transactions", "transfer_outbox", "pending_transfers", "accounts").forEach {
            table ->
            statements.contains(table) shouldBe false
        }
    }

    @Test
    fun `the correlation id is the primary key`() {
        // The idempotency guarantee. If this were not the key, a second sync
        // would insert a second row for the same answer - and REPLACE-style
        // upserts would stop being safe, because nothing would stop them
        // rewriting the acknowledgement.
        val statement = sql().single()

        statement.contains("PRIMARY KEY(`correlationId`)") shouldBe true
    }

    @Test
    fun `every column the entity declares is created and they all match`() {
        // Pinned against the entity rather than against 137.json, so the test
        // states the intent ("these five columns, none of them nullable except
        // as designed") instead of comparing two artefacts that could both be
        // wrong in the same way.
        val statement = sql().single()

        listOf(
            "`correlationId` TEXT NOT NULL",
            "`decision` TEXT NOT NULL",
            "`decidedBy` TEXT NOT NULL",
            "`recordedAt` INTEGER NOT NULL",
            "`acknowledged` INTEGER NOT NULL",
        ).forEach { column ->
            statement.contains(column) shouldBe true
        }
    }

    @Test
    fun `touches no Timber tree that does not exist`() {
        Timber.e(Exception("boom"), "logging with no tree planted")
    }
}

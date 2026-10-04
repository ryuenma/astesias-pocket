package com.ivy.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.data.db.migration.Migration133to134_BillReminderDueDay
import com.ivy.data.db.migration.Migration134to135_PendingBills
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Both migrations, asserted against a real SQLite database.
 *
 * The unit tests in `BillReminderInboxTest` cover the decision rules against a
 * fake, and a fake cannot prove the thing that actually stops a duplicate: SQLite
 * refusing a second row with the same primary key. That refusal is the backstop
 * for two deliveries in flight at once, where both read "not seen yet" before
 * either commits and no application-level check can see the other. So it is
 * asserted here, on the engine, rather than assumed - it is the same reason
 * `Migration132to133Test` exists for `pending_transfers`.
 */
class Migration133to134to135Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IvyRoomDatabase::class.java,
        listOf(IvyRoomDatabase.DeleteSEMigration()),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val dueDayMigration = Migration133to134_BillReminderDueDay()
    private val pendingBillsMigration = Migration134to135_PendingBills()

    private val bothMigrations = arrayOf(dueDayMigration, pendingBillsMigration)

    /**
     * A brand-new empty v135 database for one test.
     *
     * `createDatabase` first is required, not stylistic: the helper refuses to
     * migrate a database it has not created. It is also what makes these tests
     * independent - a name nothing else has created guarantees no rows leak in
     * from another test or an earlier run. `runMigrationsAndValidate` REUSES an
     * existing file of the same name, so a shared name surfaces one test's rows as
     * phantom UNIQUE violations in the next.
     */
    private fun freshDb(name: String): SupportSQLiteDatabase {
        helper.createDatabase(name, 133).close()
        return helper.runMigrationsAndValidate(name, 135, true, *bothMigrations)
    }

    // ---------------------------------------------------------------------
    // 133 -> 134: the rule's due day
    // ---------------------------------------------------------------------

    /**
     * Existing rules survive the migration and mean "no reminder".
     *
     * The load-bearing property of a nullable column added to a live table: every
     * row that was there before must still be there, and must still mean what it
     * meant. A migration that started reminding people about bills they had never
     * asked to be reminded about would be a notification arriving on a date nobody
     * chose, on every existing install, all at once.
     */
    @Test
    fun existingRulesSurviveAndReadAsNoReminder() {
        helper.createDatabase("rules-survive", 133).apply {
            insertRule(id = UUID.randomUUID(), title = "Internet", amount = 500_000.0)
            insertRule(id = UUID.randomUUID(), title = "Rent", amount = 3_000_000.0)
            close()
        }

        val newDb = helper.runMigrationsAndValidate("rules-survive", 135, true, *bothMigrations)

        newDb.query("SELECT * FROM planned_payment_rules").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("title")) shouldBe "Internet"
            // Null, and null is the whole point: "this rule has no reminder".
            cursor.isNull(cursor.getColumnIndexOrThrow("dueDayOfMonth")) shouldBe true

            cursor.moveToNext() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("title")) shouldBe "Rent"
            cursor.isNull(cursor.getColumnIndexOrThrow("dueDayOfMonth")) shouldBe true

            cursor.moveToNext() shouldBe false
        }
        newDb.close()
    }

    /** The new column round-trips a value, so a saved day is really persisted. */
    @Test
    fun aDueDayIsWrittenAndReadBack() {
        val newDb = freshDb("due-day-roundtrip")

        newDb.execSQL(
            "UPDATE planned_payment_rules SET dueDayOfMonth = 25 WHERE title = ?",
            arrayOf<Any?>("Internet"),
        )

        newDb.query(
            "SELECT dueDayOfMonth FROM planned_payment_rules WHERE title = ?",
            arrayOf<Any?>("Internet"),
        ).use { cursor ->
            // The bind is what makes this specific; without it the WHERE matches
            // nothing and the row looks absent.
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 25
        }
        newDb.close()
    }

    /**
     * The filter that decides who gets a reminder excludes the unconfigured.
     *
     * Mirrors `PlannedPaymentRuleDao.findAllWithBillReminder`. Asserted as SQL
     * because the query text and the column's nullability have to agree, and
     * nothing else in the build would notice if they drifted.
     */
    @Test
    fun onlyRulesWithADaySetAreFoundByTheReminderQuery() {
        helper.createDatabase("reminder-filter", 133).apply {
            insertRule(id = UUID.randomUUID(), title = "Internet", amount = 500_000.0)
            insertRule(id = UUID.randomUUID(), title = "Rent", amount = 3_000_000.0)
            close()
        }
        val newDb = helper.runMigrationsAndValidate("reminder-filter", 135, true, *bothMigrations)

        // Give exactly one rule a day.
        newDb.execSQL(
            "UPDATE planned_payment_rules SET dueDayOfMonth = 25 WHERE title = ?",
            arrayOf<Any?>("Internet"),
        )

        newDb.query(
            "SELECT * FROM planned_payment_rules " +
                "WHERE isDeleted = 0 AND dueDayOfMonth IS NOT NULL",
        ).use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(cursor.getColumnIndexOrThrow("title")) shouldBe "Internet"
            // The other rule is excluded rather than defaulted to some day.
            cursor.moveToNext() shouldBe false
        }
        newDb.close()
    }

    // ---------------------------------------------------------------------
    // 134 -> 135: the pending-bill inbox
    // ---------------------------------------------------------------------

    /**
     * THE idempotency guarantee, on the engine rather than in a mock.
     *
     * Two deliveries of one reminder. The second must be refused by the primary
     * key, so the replay cannot become a second pending bill. This is the failure
     * this project has already shipped once on the server, where a PocketBase
     * collection declared `unique: true` on a field with no real index behind it,
     * so a replayed POST returned 200 and recorded the same transfer twice.
     */
    @Test
    fun theSameOccurrenceKeyCannotBeInsertedTwice() {
        val occurrenceKey = "reminder:[aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee]:[2027-01]"
        val newDb = freshDb("pending-bills-idempotency")

        newDb.execSQL(insertSql(), insertArgs(occurrenceKey))

        // The replay: a WorkManager retry, a scheduler re-run, a reboot mid-write
        // or a re-sync all arrive holding this identical derived key.
        val replayRefused = runCatching {
            newDb.execSQL(insertSql(), insertArgs(occurrenceKey))
        }.isFailure

        replayRefused shouldBe true

        newDb.query("SELECT COUNT(*) FROM pending_bills").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 1
        }
        newDb.close()
    }

    /**
     * What actually stops the duplicate is IGNORE, not REPLACE.
     *
     * The real writer uses `INSERT OR IGNORE` and branches on the row count. This
     * asserts the resulting behaviour directly: a second insert changes nothing,
     * so a reminder that replays after the owner has adjusted the amount cannot
     * silently reset it back to the rule's figure. An owner who corrected a bill
     * and watched it snap back would conclude the app had ignored him.
     */
    @Test
    fun aReplayLeavesAnAdjustedAmountAlone() {
        val occurrenceKey = "reminder:[bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee]:[2027-01]"
        val newDb = freshDb("pending-bills-amount-preserved")

        newDb.execSQL(insertSql(), insertArgs(occurrenceKey, amount = 500_000.0))

        // The owner corrects the bill.
        newDb.compileStatement(
            "UPDATE pending_bills SET amount = ? WHERE occurrenceKey = ?",
        ).use { statement ->
            statement.bindDouble(1, 650_000.0)
            statement.bindString(2, occurrenceKey)
            statement.executeUpdateDelete() shouldBe 1
        }

        // The replay is ignored, so the correction stands.
        newDb.execSQL(
            insertSql().replace("INSERT INTO", "INSERT OR IGNORE INTO"),
            insertArgs(occurrenceKey, amount = 500_000.0),
        )

        newDb.query(
            "SELECT amount FROM pending_bills WHERE occurrenceKey = ?",
            arrayOf<Any>(occurrenceKey),
        ).use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getDouble(0) shouldBe 650_000.0
        }
        newDb.close()
    }

    /**
     * Pending bills are invisible to the ledger.
     *
     * The structural guarantee behind owner constraint 1: an unpaid bill must
     * never move a balance. Every balance, report, CSV export and XLSX export in
     * this codebase reads `transactions`, and nothing in `pending_bills` is ever
     * in it - so this cannot be counted by any of them, today or after a query
     * somebody adds next month.
     *
     * Asserted against the real ledger rather than by reading the code, because
     * the claim is about two tables not being connected.
     */
    @Test
    fun pendingBillsCannotAffectAnyTotal() {
        val newDb = freshDb("pending-bills-outside-ledger")

        // A real transaction the owner actually made.
        newDb.execSQL(insertTransactionSql(), arrayOf<Any?>(UUID.randomUUID().toString(), 1_000_000.0))
        // A bill that has been reminded but not paid.
        newDb.execSQL(insertSql(), insertArgs("reminder:[cccccccc-bbbb-cccc-dddd-eeeeeeeeeeee]:[2027-01]"))

        // The ledger holds exactly the one real transaction, and the balance that
        // reads it cannot see the pending bill at all.
        newDb.query("SELECT COUNT(*) FROM transactions").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 1
        }
        newDb.query(
            "SELECT COALESCE(SUM(amount), 0) FROM transactions",
        ).use { cursor ->
            cursor.moveToFirst() shouldBe true
            // 1,000,000 only. The 500,000 pending bill is not in this sum, and
            // never can be, because it is not a row here.
            cursor.getDouble(0) shouldBe 1_000_000.0
        }

        // And it is nonetheless visible to the owner, in its own table.
        newDb.query("SELECT COUNT(*) FROM pending_bills").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 1
        }
        newDb.close()
    }

    /**
     * The inbox starts empty, and existing transactions are untouched.
     *
     * An owner who has set no reminder gets an empty inbox rather than a
     * fabricated one, and the migration invents no data.
     */
    @Test
    fun theInboxStartsEmpty() {
        helper.createDatabase("inbox-empty", 133).apply {
            insertTransaction(UUID.randomUUID().toString(), 42_000.0)
            close()
        }

        val newDb = helper.runMigrationsAndValidate("inbox-empty", 135, true, *bothMigrations)

        newDb.query("SELECT COUNT(*) FROM pending_bills").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 0
        }
        newDb.query("SELECT COUNT(*) FROM transactions").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getInt(0) shouldBe 1
        }
        newDb.close()
    }

    /**
     * A settled bill keeps its row, so a replay cannot resurrect it.
     *
     * Why PAID and DISMISSED are tombstones rather than deletions: a deletion
     * would let the next reminder for the same occurrence find nothing and insert
     * a fresh PENDING bill, so the owner would be asked to pay a bill he had
     * already paid, once per replay, with nothing left recording that he had said
     * yes.
     */
    @Test
    fun aSettledBillSurvivesSoAReplayCannotResurrectIt() {
        val occurrenceKey = "reminder:[dddddddd-bbbb-cccc-dddd-eeeeeeeeeeee]:[2027-01]"
        val newDb = freshDb("pending-bills-tombstone")

        newDb.execSQL(insertSql(), insertArgs(occurrenceKey))

        // He pays it. Guarded on PENDING, exactly like the DAO's markState.
        newDb.compileStatement(
            "UPDATE pending_bills SET state = ? WHERE occurrenceKey = ? AND state = ?",
        ).use { statement ->
            statement.bindString(1, "PAID")
            statement.bindString(2, occurrenceKey)
            statement.bindString(3, "PENDING")
            statement.executeUpdateDelete() shouldBe 1
        }

        // The tombstone is still there.
        newDb.query(
            "SELECT state FROM pending_bills WHERE occurrenceKey = ?",
            arrayOf<Any>(occurrenceKey),
        ).use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(0) shouldBe "PAID"
        }

        // And a replay cannot flip it back to PENDING...
        newDb.compileStatement(
            "UPDATE pending_bills SET state = ? WHERE occurrenceKey = ? AND state = ?",
        ).use { statement ->
            statement.bindString(1, "PENDING")
            statement.bindString(2, occurrenceKey)
            statement.bindString(3, "PENDING")
            // Guarded on PENDING, and the row is PAID, so this matches nothing.
            statement.executeUpdateDelete() shouldBe 0
        }

        // ...nor insert a fresh PENDING row under a new key for the same bill.
        val secondKey = "reminder:[dddddddd-bbbb-cccc-dddd-eeeeeeeeeeee]:[2027-02]"
        newDb.execSQL(insertSql(), insertArgs(secondKey, state = "PAID"))
        newDb.query("SELECT COUNT(*) FROM pending_bills WHERE state = 'PENDING'").use { cursor ->
            cursor.moveToFirst() shouldBe true
            // Nothing is waiting on him: the paid bill is not in his inbox.
            cursor.getInt(0) shouldBe 0
        }
        newDb.close()
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    private fun insertSql(): String =
        """
        INSERT INTO pending_bills (
            occurrenceKey, ruleId, dueOn, amount, title, description,
            accountId, type, state
        ) VALUES (?,?,?,?,?,?,?,?,?)
        """.trimIndent()

    /**
     * `Array<Any?>` rather than `Array<Any>` because the nullable title and
     * description columns are exactly what a minimal bill looks like, and
     * `arrayOf<Any>` cannot hold a null at all.
     */
    private fun insertArgs(
        occurrenceKey: String,
        amount: Double = 500_000.0,
        state: String = "PENDING",
    ): Array<Any?> = arrayOf(
        occurrenceKey, UUID.randomUUID().toString(), 1_799_769_600_000L, amount,
        "Internet", null, UUID.randomUUID().toString(), "EXPENSE", state,
    )

    private fun insertTransactionSql(): String =
        """
        INSERT INTO transactions (
            accountId, type, amount, toAccountId, toAmount, title, description,
            dateTime, categoryId, dueDate, recurringRuleId, paidForDateTime,
            attachmentUrl, counterpartProfileId, linkedTransactionId, loanId,
            loanRecordId, isSynced, isDeleted, id
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()

    /** Inserts a v133-shaped planned payment rule; v133 has no `dueDayOfMonth`. */
    private fun SupportSQLiteDatabase.insertRule(
        id: UUID,
        title: String,
        amount: Double,
    ) {
        execSQL(
            """
            INSERT INTO planned_payment_rules (
                startDate, intervalN, intervalType, oneTime, type, accountId,
                amount, categoryId, title, description, isSynced, isDeleted, id
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf<Any?>(
                1_767_225_300_000L, 1, "MONTH", 0, "EXPENSE",
                UUID.randomUUID().toString(), amount, null, title, null, 0, 0, id.toString(),
            ),
        )
    }
}
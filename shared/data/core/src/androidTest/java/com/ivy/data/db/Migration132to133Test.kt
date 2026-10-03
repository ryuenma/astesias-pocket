package com.ivy.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.data.db.migration.Migration132to133_PendingTransfers
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * The pending-transfer inbox, asserted against a real SQLite database.
 *
 * The unit tests in `TransferInboxTest` cover the decision rules, and they cannot
 * cover the thing that actually stops a duplicate: SQLite refusing a second row
 * with the same primary key. That is the backstop for two deliveries in flight
 * at once, where both read "not seen yet" before either commits, and no
 * application-level check can see the other. So it is asserted here, on the
 * engine, rather than assumed.
 */
class Migration132to133Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IvyRoomDatabase::class.java,
        listOf(IvyRoomDatabase.DeleteSEMigration()),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val migration = Migration132to133_PendingTransfers()

    /**
     * A brand-new empty v133 database for one test.
     *
     * `createDatabase` first is required, not stylistic: the helper refuses to
     * migrate a database it has not created. It is also what makes these tests
     * independent - a name nothing else has created guarantees no rows leak in
     * from another test or from an earlier run.
     */
    private fun freshDb(name: String): SupportSQLiteDatabase {
        helper.createDatabase(name, 132).close()
        return helper.runMigrationsAndValidate(name, 133, true, migration)
    }

    @Test
    fun theInboxStartsEmptyAndExistingTransactionsAreUntouched() {
        // Given a v132 database holding real transactions
        helper.createDatabase(TestDb, 132).apply {
            insertTransaction(id = UUID.randomUUID(), amount = 42_000.0)
            insertTransaction(id = UUID.randomUUID(), amount = 7_000.0)
            close()
        }

        // When
        val newDb = helper.runMigrationsAndValidate(TestDb, 133, true, migration)

        // Then the ledger is intact...
        newDb.query("SELECT COUNT(*) FROM transactions").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 2
        }
        // ...and the inbox is empty, because nothing has arrived yet. A user who
        // has never received a partner transfer must not see invented items.
        newDb.query("SELECT COUNT(*) FROM pending_transfers").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 0
        }
        newDb.close()
    }

    @Test
    fun theSameCorrelationIdCannotBeInsertedTwice() {
        // THE idempotency guarantee, on the engine rather than in a mock.
        val correlationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val newDb = freshDb("idem-replay")

        newDb.execSQL(
            "INSERT INTO pending_transfers (" +
                "correlationId, recordedByProfileId, recordedByProfileName, intoAccountId, " +
                "fromAccountName, amount, currency, occurredOn, title, description, state" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            insertArgs(correlationId, title = "Transfer")
        )

        // A replayed delivery of the same transfer. This is what the owner-side
        // retry after a dropped response looks like, and what two in-flight POSTs
        // for one transfer look like from this device's side.
        val replayRejected = runCatching {
            newDb.execSQL(
                "INSERT INTO pending_transfers (" +
                    "correlationId, recordedByProfileId, recordedByProfileName, intoAccountId, " +
                    "fromAccountName, amount, currency, occurredOn, title, description, state" +
                    ") VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                insertArgs(correlationId, title = "Transfer")
            )
        }.isFailure

        // The insert is refused, so the replay cannot become a second pending item.
        replayRejected shouldBe true

        newDb.query("SELECT COUNT(*) FROM pending_transfers").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 1
        }
        newDb.close()
    }

    @Test
    fun aDeclinedTransferSurvivesSoAReplayCannotResurrectIt() {
        // Why DECLINED is a retained row rather than a deleted one. If declining
        // deleted, the next replay would find nothing and insert a fresh PENDING
        // item - and the transfer she dismissed would return on every sync, with
        // no record left that she had ever said no.
        val correlationId = UUID.fromString("bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee")
        val newDb = freshDb("decline-tombstone")

        newDb.execSQL(
            "INSERT INTO pending_transfers (" +
                "correlationId, recordedByProfileId, recordedByProfileName, intoAccountId, " +
                "fromAccountName, amount, currency, occurredOn, title, description, state" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            insertArgs(correlationId)
        )

        // She declines. Guarded on PENDING, exactly like the DAO's markDecided.
        val updated = newDb.compileStatement(
            "UPDATE pending_transfers SET state = 'DECLINED' " +
                "WHERE correlationId = ? AND state = 'PENDING'"
        ).use { statement ->
            statement.bindString(1, correlationId.toString())
            statement.executeUpdateDelete()
        }
        updated shouldBe 1

        // The tombstone is still there. The bind is what makes this query specific -
        // without it the WHERE clause matches nothing and the row looks absent.
        newDb.query(
            "SELECT state FROM pending_transfers WHERE correlationId = ?",
            arrayOf<Any>(correlationId.toString())
        ).use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(0) shouldBe "DECLINED"
        }

        // ...so a replay cannot overwrite the decision back to PENDING. An INSERT
        // collides on the primary key; an UPDATE guarded on PENDING matches nothing.
        val resurrectBlocked = newDb.compileStatement(
            "UPDATE pending_transfers SET state = 'PENDING' WHERE correlationId = ? AND state = 'PENDING'"
        ).use { statement ->
            statement.bindString(1, correlationId.toString())
            statement.executeUpdateDelete()
        }
        resurrectBlocked shouldBe 0

        // And it is gone from what she is asked to confirm.
        newDb.query("SELECT COUNT(*) FROM pending_transfers WHERE state = 'PENDING'").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) shouldBe 0
        }
        newDb.close()
    }

    @Test
    fun pendingItemsAreInvisibleToTheLedger() {
        // The structural guarantee behind "visibly pending, not a normal
        // TRANSFER". Every balance, report and export reads `transactions`; nothing
        // in that table knows this inbox exists, so an unconfirmed item cannot be
        // totalled into anyone's balance.
        val newDb = freshDb("inbox-outside-ledger")
        newDb.execSQL(
            "INSERT INTO pending_transfers (" +
                "correlationId, recordedByProfileId, recordedByProfileName, intoAccountId, " +
                "fromAccountName, amount, currency, occurredOn, title, description, state" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            insertArgs(UUID.randomUUID())
        )

        newDb.query("SELECT COUNT(*) FROM transactions").use { cursor ->
            cursor.moveToFirst()
            // The ledger is untouched by a pending item: no row, no amount.
            cursor.getInt(0) shouldBe 0
        }
        newDb.close()
    }

    /** Inserts a v132-shaped transaction; v132 has no pending-transfer columns. */
    private fun SupportSQLiteDatabase.insertTransaction(id: UUID, amount: Double) {
        execSQL(
            """
            INSERT INTO transactions (
                accountId, type, amount, toAccountId, toAmount, title, description,
                dateTime, categoryId, dueDate, recurringRuleId, paidForDateTime,
                attachmentUrl, counterpartProfileId, linkedTransactionId, loanId,
                loanRecordId, isSynced, isDeleted, id
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf<Any?>(
                UUID.randomUUID().toString(), "EXPENSE", amount, null, null, null, null,
                1_767_225_300_000L, null, null, null, null, null, null, null, null,
                null, 0, 0, id.toString(),
            )
        )
    }

    /**
     * Bind arguments for one pending-transfer INSERT.
     *
     * `Array<Any?>` rather than `Array<Any>` because the nullable title and
     * description columns are exactly what a minimal event looks like, and
     * `arrayOf<Any>` cannot hold a null at all.
     */
    private fun insertArgs(correlationId: UUID, title: String? = null): Array<Any?> = arrayOf(
        correlationId.toString(), "default", "Partner", UUID.randomUUID().toString(),
        "His Bank", 500_000.0, "IDR", 1_767_225_300_000L, title, null, "PENDING",
    )

    companion object {
        private const val TestDb = "migration-test-132-133"
    }
}
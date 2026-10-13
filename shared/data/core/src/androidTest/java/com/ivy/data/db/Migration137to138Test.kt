package com.ivy.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.data.db.migration.Migration136to137_TransferConfirmations
import com.ivy.data.db.migration.Migration137to138_DirectoryAccountProfileKey
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * `Migration137to138_DirectoryAccountProfileKey`, asserted against a real SQLite
 * database - the only place the thing that actually broke can be proven.
 *
 * ## What is under test, and why a mock cannot reach it
 *
 * `directory_accounts` declared `accountId` as its sole primary key while the
 * server's uniqueness is the composite `(profileId, localAccountId)`, and
 * `insertAll` uses `OnConflictStrategy.REPLACE`. So a refresh that fetched both
 * profiles' rows wrote 30 rows into a table that holds a given accountId ONCE,
 * and the later profile silently overwrote the earlier: measured on a device
 * paired as `qa_a`, the table ended up holding 21 rows, all `qa_a`, zero `qa_b`.
 * `findOthers` then returned nothing and the picker read "No other profiles yet"
 * for ever, on a healthy sync token.
 *
 * The loss happens at the KEY, below the repository, so [refresh behaviour] lives
 * in the JVM test `DirectoryAccountProfileKeyTest` and everything about the key
 * itself lives here - on the engine, because only SQLite refuses a duplicate
 * primary key and only SQLite can be shown to keep both rows afterwards.
 *
 * `runMigrationsAndValidate` is load-bearing rather than decorative: it compares
 * the migrated schema against the schema Room exported FROM the entity, so a
 * migration that drifts from [com.ivy.data.db.entity.DirectoryAccountEntity]
 * fails here instead of shipping a table the DAO cannot read.
 */
class Migration137to138Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IvyRoomDatabase::class.java,
        listOf(IvyRoomDatabase.DeleteSEMigration()),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val migration = Migration137to138_DirectoryAccountProfileKey()

    /**
     * A v137-shaped table with the rows a device actually held: this profile's
     * accounts, under the old single-column key.
     *
     * [sharedId] is an account id that BOTH profiles publish - nine of the thirty
     * rows on the real qa_a device were like this, which is what makes the bug
     * a certainty there rather than a possibility.
     */
    private val sharedId = UUID.fromString("18d55029-0000-4000-8000-000000000001")

    /** Only `qa_a` publishes this one, so it must survive untouched. */
    private val mineOnlyId = UUID.fromString("00000000-0000-4000-8000-00000000000a")

    private fun insertV137Row(
        db: SupportSQLiteDatabase,
        accountId: UUID,
        profileId: String,
        profileName: String,
        name: String,
        currencyCode: String? = "IDR",
    ) {
        db.execSQL(
            """
            INSERT INTO directory_accounts
                (accountId, profileId, profileName, name, currencyCode)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                accountId.toString(), profileId, profileName, name, currencyCode,
            ),
        )
    }

    private fun rowsPerProfile(db: SupportSQLiteDatabase): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        db.query("SELECT profileId, COUNT(*) FROM directory_accounts GROUP BY profileId").use {
            while (it.moveToNext()) {
                counts[it.getString(0)] = it.getInt(1)
            }
        }
        return counts
    }

    @Test
    fun theMigratedTableIsKeyedOnProfileIdAndAccountIdTogether() {
        val db = emptyV137("key-shape")

        // Read from the engine, not from the entity: `pk` is 1 for each column of
        // the primary key, in key order. Under the bug this read accountId=1,
        // profileId=0.
        val keyColumns = mutableListOf<String>()
        db.query("PRAGMA table_info(directory_accounts)").use {
            while (it.moveToNext()) {
                if (it.getInt(it.getColumnIndexOrThrow("pk")) > 0) {
                    keyColumns += it.getString(it.getColumnIndexOrThrow("name"))
                }
            }
        }
        db.close()

        keyColumns shouldBe listOf("profileId", "accountId")
    }

    @Test
    fun everyRowThatExistedBeforeTheMigrationIsStillThere() {
        helper.createDatabase("rows-survive", 137).apply {
            insertV137Row(this, sharedId, "qa_a", "QA A", "Mandiri")
            insertV137Row(this, mineOnlyId, "qa_a", "QA A", "Jago")
            close()
        }

        val db = migrated("rows-survive")

        // The old key made accountId unique across the WHOLE table, so every
        // surviving row already had a distinct (profileId, accountId) pair and the
        // new key cannot refuse any of them. Both ids, both names, both kept.
        rowsPerProfile(db) shouldBe mapOf("qa_a" to 2)
        db.query(
            "SELECT name FROM directory_accounts ORDER BY name",
        ).use {
            it.moveToNext() shouldBe true
            it.getString(0) shouldBe "Jago"

            it.moveToNext() shouldBe true
            it.getString(0) shouldBe "Mandiri"

            it.moveToNext() shouldBe false
        }
        db.close()
    }

    @Test
    fun bothProfilesKeepTheirOwnRowForTheSameAccountId() {
        // THE regression, on the engine. This insert is impossible on the old
        // schema: a second row with the same accountId collides with the single
        // column key and, under insertAll's REPLACE, silently overwrites the
        // first. After the migration the pair is two rows and both are readable.
        helper.createDatabase("two-profiles", 137).apply {
            insertV137Row(this, sharedId, "qa_a", "QA A", "Mandiri")
            close()
        }

        val db = migrated("two-profiles")

        db.execSQL(
            """
            INSERT INTO directory_accounts
                (accountId, profileId, profileName, name, currencyCode)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                sharedId.toString(), "qa_b", "QA B", "Mandiri (miliknya)", "IDR",
            ),
        )

        // One row per profile, each keeping its own display name - which is what
        // the picker renders, and the only way the destination is identifiable.
        rowsPerProfile(db) shouldBe mapOf("qa_a" to 1, "qa_b" to 1)
        db.query(
            "SELECT profileName, name FROM directory_accounts WHERE accountId = ? " +
                "ORDER BY profileId",
            arrayOf<Any?>(sharedId.toString()),
        ).use {
            it.moveToNext() shouldBe true
            it.getString(0) shouldBe "QA A"
            it.getString(1) shouldBe "Mandiri"

            it.moveToNext() shouldBe true
            it.getString(0) shouldBe "QA B"
            it.getString(1) shouldBe "Mandiri (miliknya)"

            it.moveToNext() shouldBe false
        }

        // And the query the picker actually makes now returns the partner.
        db.query(
            "SELECT COUNT(*) FROM directory_accounts WHERE profileId != ?",
            arrayOf<Any?>("qa_a"),
        ).use {
            it.moveToFirst() shouldBe true
            it.getInt(0) shouldBe 1
        }
        db.close()
    }

    @Test
    fun rePublishingOneAccountUnderOneProfileUpdatesInPlace() {
        // The reason the key is not merely unique-but-wrong: REPLACE on the
        // composite key is the upsert that propagates a rename, and it must touch
        // only the row it belongs to.
        val db = emptyV137("rename-upsert")

        db.execSQL(
            """
            INSERT OR REPLACE INTO directory_accounts
                (accountId, profileId, profileName, name, currencyCode)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(sharedId.toString(), "qa_a", "QA A", "Mandiri (renamed)", "IDR"),
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO directory_accounts
                (accountId, profileId, profileName, name, currencyCode)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(sharedId.toString(), "qa_b", "QA B", "Mandiri", "IDR"),
        )
        // The same account, same profile, new name: a re-sync.
        db.execSQL(
            """
            INSERT OR REPLACE INTO directory_accounts
                (accountId, profileId, profileName, name, currencyCode)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(sharedId.toString(), "qa_a", "QA A", "Mandiri v2", "IDR"),
        )

        rowsPerProfile(db) shouldBe mapOf("qa_a" to 1, "qa_b" to 1)
        db.query(
            "SELECT name FROM directory_accounts WHERE profileId = ? AND accountId = ?",
            arrayOf<Any?>("qa_a", sharedId.toString()),
        ).use {
            it.moveToFirst() shouldBe true
            it.getString(0) shouldBe "Mandiri v2"
        }
        db.query(
            "SELECT name FROM directory_accounts WHERE profileId = ? AND accountId = ?",
            arrayOf<Any?>("qa_b", sharedId.toString()),
        ).use {
            it.moveToFirst() shouldBe true
            // Untouched by a write to the other profile's row.
            it.getString(0) shouldBe "Mandiri"
        }
        db.close()
    }

    @Test
    fun theMigrationLosesNoRowAndInventsNoRow() {
        // Stated as counts in both directions: a rebuild that dropped a row would
        // blank part of the picker, and one that duplicated rows would offer the
        // same destination twice.
        helper.createDatabase("no-loss", 137).apply {
            insertV137Row(this, sharedId, "qa_a", "QA A", "Mandiri")
            insertV137Row(this, mineOnlyId, "qa_a", "QA A", "Jago")
            close()
        }

        val db = migrated("no-loss")

        db.query("SELECT COUNT(*) FROM directory_accounts").use {
            it.moveToFirst() shouldBe true
            it.getInt(0) shouldBe 2
        }
        db.query("SELECT COUNT(*) FROM directory_accounts WHERE name = ?", arrayOf<Any?>("Mandiri"))
            .use {
                it.moveToFirst() shouldBe true
                it.getInt(0) shouldBe 1
            }
        db.close()
    }

    @Test
    fun theOldProfileIdIndexIsGoneBecauseTheKeyNowCoversIt() {
        // `profileId` LEADS the primary key, so it is already indexed and
        // findOthers/findByProfile are served by the primary key index. Keeping
        // the old index would mean two index writes per insert for one too many.
        val db = emptyV137("index-dropped")

        val indexes = mutableListOf<String>()
        db.query("PRAGMA index_list(directory_accounts)").use {
            while (it.moveToNext()) {
                indexes += it.getString(it.getColumnIndexOrThrow("name"))
            }
        }
        db.close()

        indexes.none { it.contains("profileId") } shouldBe true
    }

    @Test
    fun anEmptyDirectoryMigratesToAnEmptyDirectory() {
        // A device that never synced has nothing to carry across, and the
        // migration must not fabricate rows to fill the new shape.
        val db = emptyV137("empty")

        db.query("SELECT COUNT(*) FROM directory_accounts").use {
            it.moveToFirst() shouldBe true
            it.getInt(0) shouldBe 0
        }
        db.close()
    }

    /**
     * Migrate an already-seeded v137 database to 138 and validate the result
     * against the entity's own schema.
     *
     * The caller must have created the database first (via
     * [helper.createDatabase] or [emptyV137]). This helper does NOT create it,
     * because `createDatabase` truncates: a helper that did both would silently
     * discard whatever rows the test had just seeded and every assertion about
     * preserved data would read as a clean-slate migration instead - which is
     * exactly the failure mode this file exists to catch.
     */
    private fun migrated(name: String): SupportSQLiteDatabase =
        helper.runMigrationsAndValidate(
            name,
            138,
            true,
            Migration136to137_TransferConfirmations(),
            migration,
        )

    /**
     * An empty v137 database, for tests that only care about the migrated shape.
     *
     * `createDatabase` first is required, not stylistic: the helper refuses to
     * migrate a database it has not created. Each test uses its own name, because
     * `runMigrationsAndValidate` REUSES an existing file of the same name - a
     * shared name would surface one test's rows as phantom data in the next.
     */
    private fun emptyV137(name: String): SupportSQLiteDatabase =
        helper.createDatabase(name, 137).close().let { migrated(name) }
}

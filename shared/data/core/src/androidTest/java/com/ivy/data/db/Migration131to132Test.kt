package com.ivy.data.db

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.data.db.migration.Migration131to132_CategorySubcategories
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class Migration131to132Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IvyRoomDatabase::class.java,
        listOf(IvyRoomDatabase.DeleteSEMigration()),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val migration = Migration131to132_CategorySubcategories()

    @Test
    fun existingCategoriesSurviveAndBecomeTopLevel() = run {
        // Given a v131 database with the categories a real install would have
        helper.createDatabase(TestDb, 131).apply {
            insertCategory(name = "Food", id = UUID.randomUUID())
            insertCategory(name = "Rent", id = UUID.randomUUID())
            close()
        }

        // When
        val newDb = helper.runMigrationsAndValidate(TestDb, 132, true, migration)

        // Then both rows survive, and both read as top-level
        newDb.query("SELECT * FROM categories").apply {
            moveToFirst() shouldBe true
            getString(getColumnIndexOrThrow("name")) shouldBe "Food"
            isNull(getColumnIndexOrThrow("parentId")) shouldBe true

            moveToNext() shouldBe true
            getString(getColumnIndexOrThrow("name")) shouldBe "Rent"
            isNull(getColumnIndexOrThrow("parentId")) shouldBe true

            moveToNext() shouldBe false
            close()
        }
        newDb.close()
    }

    @Test
    fun parentIdIsWrittenAndQueryable() = run {
        // Given
        val food = UUID.randomUUID()
        val groceries = UUID.randomUUID()
        helper.createDatabase(TestDb, 131).apply {
            insertCategory(name = "Food", id = food)
            insertCategory(name = "Groceries", id = groceries)
            close()
        }

        // When
        val newDb = helper.runMigrationsAndValidate(TestDb, 132, true, migration)
        newDb.execSQL(
            "UPDATE categories SET parentId = ? WHERE id = ?",
            arrayOf<Any>(food.toString(), groceries.toString())
        )

        // Then the tree queries resolve it
        newDb.query("SELECT * FROM categories WHERE parentId IS NULL").use { cursor: Cursor ->
            cursor.count shouldBe 1
            cursor.moveToFirst()
            cursor.getString(cursor.getColumnIndexOrThrow("name")) shouldBe "Food"
        }
        newDb.query("SELECT * FROM categories WHERE parentId = ?", arrayOf<Any>(food.toString()))
            .use { cursor: Cursor ->
                cursor.count shouldBe 1
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndexOrThrow("name")) shouldBe "Groceries"
            }
        newDb.close()
    }

    /**
     * Inserts into the pre-migration table, which has no `parentId` column - so
     * the column list here must stay exactly what v131 knew about.
     */
    private fun SupportSQLiteDatabase.insertCategory(name: String, id: UUID) {
        val sql = """
        INSERT INTO categories (
            name, color, icon, orderNum, isSynced, isDeleted, id
        ) VALUES (
            ?, ?, ?, ?, ?, ?, ?
        )
    """.trimIndent()
        val statement = this.compileStatement(sql)

        statement.bindString(1, name)
        statement.bindLong(2, 42L)
        statement.bindNull(3)
        statement.bindDouble(4, 1.0)
        statement.bindLong(5, 1)
        statement.bindLong(6, 0)
        statement.bindString(7, id.toString())

        statement.executeInsert()
    }

    companion object {
        private const val TestDb = "migration-test-131-132"
    }
}
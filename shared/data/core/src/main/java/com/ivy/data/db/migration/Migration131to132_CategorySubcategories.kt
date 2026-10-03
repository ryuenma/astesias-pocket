package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds subcategory support to the flat category list.
 *
 * `parentId` is nullable, and null is exactly what every existing row means
 * today: a top-level category. So the ALTER alone is the whole migration - no
 * backfill, no rewrite of the `categories` table, and every existing
 * transaction keeps pointing at a category that still means what it meant.
 *
 * Deliberately does NOT add a foreign key on `parentId`. A self-referencing FK
 * with cascade would turn "delete this category" into "delete a whole subtree of
 * the user's spending history", and without cascade it would make a delete fail
 * on rows the user cannot see from the screen they deleted from. The column is
 * indexed because every read of the category tree filters on it.
 *
 * @see com.ivy.data.db.entity.CategoryEntity
 */
class Migration131to132_CategorySubcategories : Migration(131, 132) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN parentId TEXT")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_categories_parentId` " +
                "ON `categories` (`parentId`)"
        )
    }
}
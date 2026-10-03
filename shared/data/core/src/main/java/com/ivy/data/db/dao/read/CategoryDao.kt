package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.CategoryEntity
import java.util.*

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isDeleted = :deleted ORDER BY orderNum ASC")
    suspend fun findAll(deleted: Boolean = false): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: UUID): CategoryEntity?

    /**
     * Top-level categories, i.e. the ones with no parent.
     *
     * This is the entry point for walking the tree: start here, then
     * [findSubcategories] per expanded row.
     */
    @Query("SELECT * FROM categories WHERE parentId IS NULL AND isDeleted = :deleted ORDER BY orderNum ASC")
    suspend fun findRoots(deleted: Boolean = false): List<CategoryEntity>

    /**
     * Direct children of [parentId], ordered for display.
     *
     * Read this before deleting or re-parenting a category: `parentId` carries no
     * foreign key, so a delete that ignores children silently orphans them into a
     * subtree nothing can reach.
     */
    @Query("SELECT * FROM categories WHERE parentId = :parentId AND isDeleted = :deleted ORDER BY orderNum ASC")
    suspend fun findSubcategories(parentId: UUID, deleted: Boolean = false): List<CategoryEntity>

    @Query("SELECT MAX(orderNum) FROM categories")
    suspend fun findMaxOrderNum(): Double?
}
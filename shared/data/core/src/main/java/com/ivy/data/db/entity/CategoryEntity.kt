package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

/**
 * A category, either top-level or a subcategory of one.
 *
 * [parentId] points at the `id` of the owning category, so a subcategory is a row
 * in this same table rather than a second one: transactions keep pointing at a
 * plain category id either way, and a category is promoted or demoted by
 * changing one nullable column instead of moving rows between tables.
 *
 * Null means top-level. That is what every pre-subcategory row already reads as,
 * which is what lets `Migration131to132_CategorySubcategories` add the column
 * with no backfill.
 *
 * Deliberately NOT a foreign key. FK enforcement would make deleting a parent
 * either cascade into its children or fail outright, and neither is a policy a
 * schema should pick on behalf of a user's spending history. Integrity is the
 * caller's job - see `CategoryDao.findSubcategories` for reading a parent's
 * children before deciding what to do with them.
 *
 * Indexed because the category picker reads one parent's children per expanded
 * row, so this lookup runs on every screen showing a category tree.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(
    tableName = "categories",
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @SerialName("name")
    val name: String,
    @SerialName("color")
    val color: Int,
    @SerialName("icon")
    val icon: String? = null,
    @SerialName("orderNum")
    val orderNum: Double = 0.0,
    @SerialName("parentId")
    @Serializable(with = KSerializerUUID::class)
    val parentId: UUID? = null,

    @Deprecated("Obsolete field used for cloud sync. Can't be deleted because of backwards compatibility")
    @SerialName("isSynced")
    val isSynced: Boolean = false,
    @Deprecated("Obsolete field used for cloud sync. Can't be deleted because of backwards compatibility")
    @SerialName("isDeleted")
    val isDeleted: Boolean = false,

    @PrimaryKey
    @SerialName("id")
    @Serializable(with = KSerializerUUID::class)
    val id: UUID = UUID.randomUUID()
)

package com.ivy.data.model

import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.sync.Identifiable
import com.ivy.data.model.sync.UniqueId
import java.util.UUID

@JvmInline
value class CategoryId(override val value: UUID) : UniqueId

data class Category(
    override val id: CategoryId,
    val name: NotBlankTrimmedString,
    val color: ColorInt,
    val icon: IconAsset?,
    override val orderNum: Double,
    /**
     * The owning category, or null when this is a top-level category.
     *
     * A subcategory is an ordinary [Category] rather than a distinct type, so
     * every existing consumer keeps working unchanged: the only difference is
     * that a subcategory's id appears in exactly one parent's `parentId` list.
     */
    val parentId: CategoryId? = null,
) : Identifiable<CategoryId>, Reorderable
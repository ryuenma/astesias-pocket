package com.ivy.wallet.domain.deprecated.logic

import androidx.compose.ui.graphics.toArgb
import arrow.core.raise.either
import com.ivy.data.category.CategoryTree
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.CategoryRepository
import com.ivy.legacy.utils.ioThread
import com.ivy.wallet.domain.deprecated.logic.model.CreateCategoryData
import com.ivy.wallet.domain.pure.util.nextOrderNum
import java.util.UUID
import javax.inject.Inject

class CategoryCreator @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    suspend fun createCategory(
        data: CreateCategoryData,
        onRefreshUI: suspend (Category) -> Unit
    ) {
        val name = data.name
        if (name.isBlank()) return

        try {
            val newCategory = ioThread {
                // The parent arrives from a picker listing the user's own
                // categories, so it can name a row that was deleted while the
                // modal was open. Dropping it keeps the new category a root
                // rather than storing a parentId that resolves to nothing -
                // an orphan is reachable (CategoryTree promotes it) but it is
                // not what the user asked for.
                val parentId = data.parentId
                    ?.takeIf { candidate -> categoryRepository.findById(candidate) != null }

                val newCategory: Category? = either {
                    Category(
                        name = NotBlankTrimmedString.from(name.trim()).bind(),
                        color = ColorInt(data.color.toArgb()),
                        icon = data.icon?.let(IconAsset::from)?.getOrNull(),
                        orderNum = categoryRepository.findMaxOrderNum().nextOrderNum(),
                        id = CategoryId(UUID.randomUUID()),
                        parentId = parentId,
                    )
                }.getOrNull()

                if (newCategory != null) {
                    categoryRepository.save(newCategory)
                }
                newCategory
            }

            newCategory?.let { onRefreshUI(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun editCategory(
        updatedCategory: Category,
        onRefreshUI: suspend (Category) -> Unit
    ) {
        if (updatedCategory.name.value.isBlank()) return

        try {
            val safeCategory = ioThread {
                // Re-parenting is the one place a cycle can be introduced:
                // the picker offers existing categories, so a user can aim a
                // category at itself or at one of its own children. Nothing in
                // the schema stops either, so the check lives here, on the one
                // path every edit goes through.
                val tree = CategoryTree.of(categoryRepository.findAll())
                if (tree.canBeParentOf(updatedCategory.parentId, updatedCategory)) {
                    updatedCategory
                } else {
                    updatedCategory.copy(parentId = null)
                }
            }

            ioThread {
                categoryRepository.save(safeCategory)
            }

            onRefreshUI(safeCategory)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

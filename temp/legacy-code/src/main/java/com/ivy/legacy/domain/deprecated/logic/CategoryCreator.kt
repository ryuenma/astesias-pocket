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

/**
 * What happened to a create/edit request.
 *
 * Added because the uniqueness rule has to be REPORTED, not merely enforced: a
 * caller that gets nothing back cannot tell "saved" from "rejected as a
 * duplicate", and will read both as success.
 * [CategorySaveResult.Rejected.DuplicateName] in particular is a validation
 * outcome rather than a failure, and the modal renders it inline on the name
 * field while the user is still typing instead of as a post-save dialog.
 */
sealed interface CategorySaveResult {
    data class Saved(val category: Category) : CategorySaveResult

    sealed interface Rejected : CategorySaveResult {
        /** No name to save. The modal disables Save for this, so it is rare. */
        data object BlankName : Rejected

        /**
         * A sibling already goes by this name, so storing it would recreate the
         * exact defect this class exists to prevent: a name that no longer
         * identifies exactly one category.
         */
        data object DuplicateName : Rejected

        /** The row could not be written. */
        data class Failed(val error: Throwable) : Rejected
    }
}

class CategoryCreator @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    suspend fun createCategory(
        data: CreateCategoryData,
        onRefreshUI: suspend (Category) -> Unit
    ): CategorySaveResult {
        val name = data.name
        if (name.isBlank()) return CategorySaveResult.Rejected.BlankName

        return try {
            // One type throughout the lambda: a branch returning a Category and
            // another returning a Rejected would infer a common supertype and
            // force a cast at every use.
            val result = ioThread {
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

                if (newCategory == null) {
                    return@ioThread CategorySaveResult.Rejected.BlankName
                }

                // The domain-layer gate. The modal applies the same rule live so
                // the user sees it while typing, but the modal works from a
                // snapshot while this runs against the repository, so a caller
                // with no UI validation at all is still covered.
                //
                // Checked against the EFFECTIVE parentId, i.e. after the
                // existence test above: a category that has just been demoted
                // to a root has to clear the root namespace, not that of a
                // parent that no longer exists.
                val tree = CategoryTree.of(categoryRepository.findAll())
                if (tree.hasSiblingNamed(
                        name = newCategory.name.value,
                        parentId = newCategory.parentId,
                    )
                ) {
                    return@ioThread CategorySaveResult.Rejected.DuplicateName
                }

                categoryRepository.save(newCategory)
                CategorySaveResult.Saved(newCategory)
            }

            if (result is CategorySaveResult.Saved) {
                onRefreshUI(result.category)
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            CategorySaveResult.Rejected.Failed(e)
        }
    }

    suspend fun editCategory(
        updatedCategory: Category,
        onRefreshUI: suspend (Category) -> Unit
    ): CategorySaveResult {
        if (updatedCategory.name.value.isBlank()) return CategorySaveResult.Rejected.BlankName

        return try {
            // One type throughout the lambda: a branch returning a Category and
            // another returning a Rejected would infer a common supertype and
            // force a cast at every use.
            val result = ioThread {
                // Re-parenting is the one place a cycle can be introduced:
                // the picker offers existing categories, so a user can aim a
                // category at itself or at one of its own children. Nothing in
                // the schema stops either, so the check lives here, on the one
                // path every edit goes through.
                val tree = CategoryTree.of(categoryRepository.findAll())
                val cycleSafe = if (tree.canBeParentOf(updatedCategory.parentId, updatedCategory)) {
                    updatedCategory
                } else {
                    updatedCategory.copy(parentId = null)
                }

                // Uniqueness, checked AFTER the cycle repair so the sibling
                // scope is the parent actually being stored. Two orderings
                // matter here, and both are easy to get wrong:
                //
                //  - `excluding` the edited id, so saving a category whose name
                //    is unchanged does not collide with itself. Without it,
                //    every save that does not rename anything is rejected.
                //  - against the whole tree, so MOVING a category under a new
                //    parent is checked against that parent's children rather
                //    than the ones it is leaving.
                if (tree.hasSiblingNamed(
                        name = cycleSafe.name.value,
                        parentId = cycleSafe.parentId,
                        excluding = cycleSafe.id,
                    )
                ) {
                    return@ioThread CategorySaveResult.Rejected.DuplicateName
                }

                categoryRepository.save(cycleSafe)
                CategorySaveResult.Saved(cycleSafe)
            }

            if (result is CategorySaveResult.Saved) {
                onRefreshUI(result.category)
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            CategorySaveResult.Rejected.Failed(e)
        }
    }
}

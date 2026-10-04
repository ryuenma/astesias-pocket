package com.ivy.categories

import com.ivy.data.model.Category
import com.ivy.wallet.domain.data.SortOrder
import com.ivy.wallet.ui.theme.modal.edit.CategoryModalData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// DataClassDefaultValues: matches the seven sibling properties above, all of
// which are baselined for the same reason - the screen's default state is a
// deliberate, fully-specified "nothing loaded yet" value used by previews.
@Suppress("DataClassDefaultValues")
data class CategoriesScreenState(
    val baseCurrency: String = "",
    val categories: ImmutableList<CategoryData> = persistentListOf(),
    /**
     * Every category, before the search box is applied.
     *
     * The parent selector needs this rather than [categories]: that one is
     * filtered by the query, so a search would silently shrink the set of
     * parents on offer.
     */
    val allCategories: ImmutableList<Category> = persistentListOf(),
    val reorderModalVisible: Boolean = false,
    val categoryModalData: CategoryModalData? = null,
    val sortModalVisible: Boolean = false,
    val sortOrderItems: ImmutableList<SortOrder> = SortOrder.values().toList().toImmutableList(),
    val sortOrder: SortOrder = SortOrder.DEFAULT,
    val compactCategoriesModeEnabled: Boolean,
    val showCategorySearchBar: Boolean,

)

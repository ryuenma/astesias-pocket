package com.ivy.wallet.domain.deprecated.logic.model

import androidx.compose.ui.graphics.Color
import com.ivy.data.model.CategoryId

// DataClassDefaultValues: parentId is deliberately nullable-with-default so
// that adding subcategories did not have to touch any of the 8 existing call
// sites, every one of which creates a top-level category.
// DataClassTypedIDs: false positive. CategoryId IS the type-safe id - a
// @JvmInline value class over UUID - which is exactly what this rule asks for.
// The rule simply does not know the alias.
@Suppress("DataClassDefaultValues", "DataClassTypedIDs")
data class CreateCategoryData(
    val name: String,
    val color: Color,
    val icon: String?,
    /**
     * The category this one nests under, or null for a top-level category.
     *
     * Nullable and defaulted on purpose: every existing call site keeps
     * compiling and every category created before subcategories existed stays
     * top-level, so threading this through the creation path is additive
     * rather than a migration.
     */
    val parentId: CategoryId? = null,
)

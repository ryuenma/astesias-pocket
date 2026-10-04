package com.ivy.wallet.ui.theme.modal.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.data.category.CategoryTree
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.utils.thenIf
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.utils.drawColoredShadow
import com.ivy.legacy.utils.hideKeyboard
import com.ivy.legacy.utils.onScreenStart
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.components.IvyBorderButton
import com.ivy.wallet.ui.theme.components.IvyCircleButton
import com.ivy.wallet.ui.theme.components.WrapContentRow
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalSkip
import com.ivy.wallet.ui.theme.modal.ModalTitle
import com.ivy.wallet.ui.theme.toComposeColor
import java.util.UUID

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Suppress("ParameterNaming")
@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.ChooseCategoryModal(
    id: UUID = UUID.randomUUID(),
    visible: Boolean,
    initialCategory: Category?,
    categories: List<Category>,

    showCategoryModal: (Category?) -> Unit,
    onCategoryChanged: (Category?) -> Unit,
    dismiss: () -> Unit
) {
    var selectedCategory by remember(initialCategory) {
        mutableStateOf(initialCategory)
    }

    // Which parents are showing their children. Seeded from the initial
    // category, so reopening the modal on a transaction filed under a
    // subcategory shows that subcategory instead of hiding it one level up.
    var expandedIds by remember(initialCategory) {
        mutableStateOf(
            setOfNotNull(
                initialCategory
                    ?.takeIf { it.parentId != null }
                    ?.parentId
            )
        )
    }

    IvyModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            ModalSkip {
                save(
                    category = selectedCategory,
                    onCategoryChanged = onCategoryChanged,
                    dismiss = dismiss
                )
            }
        }
    ) {
        val view = LocalView.current
        onScreenStart {
            hideKeyboard(view)
        }

        Spacer(Modifier.height(32.dp))

        ModalTitle(
            text = stringResource(R.string.choose_category)
        )

        Spacer(Modifier.height(24.dp))

        CategoryPicker(
            categories = categories,
            selectedCategory = selectedCategory,
            expandedIds = expandedIds,
            onToggleExpanded = { id ->
                expandedIds = if (id in expandedIds) {
                    expandedIds - id
                } else {
                    expandedIds + id
                }
            },
            showCategoryModal = showCategoryModal,
            onEditCategory = {
                showCategoryModal(it)
            }
        ) {
            selectedCategory = it
            save(
                shouldDismissModal = it != null,
                category = it,
                onCategoryChanged = onCategoryChanged,
                dismiss = dismiss
            )
        }

        Spacer(Modifier.height(56.dp))
    }
}

private fun save(
    shouldDismissModal: Boolean = true,

    category: Category?,
    onCategoryChanged: (Category?) -> Unit,
    dismiss: () -> Unit
) {
    onCategoryChanged(category)
    if (shouldDismissModal) {
        dismiss()
    }
}

@ExperimentalFoundationApi
@Suppress("ParameterNaming")
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedCategory: Category?,
    expandedIds: Set<CategoryId>,
    onToggleExpanded: (CategoryId) -> Unit,
    showCategoryModal: (Category?) -> Unit,
    onEditCategory: (Category) -> Unit,
    onSelected: (Category?) -> Unit,
) {
    // A category with children is an ordinary Category that happens to own
    // other categories, so the tree is derived from parentId rather than from
    // a second list the caller would have to keep in sync. See CategoryTree
    // for why the tree is built here instead of trusting a "roots" query.
    val tree = remember(categories) { CategoryTree.of(categories) }

    // Flattened to what is visible right now: every parent, plus the children
    // of whichever parents are expanded. WrapContentRow is a single flow
    // layout, so the nesting is carried by this row model plus an indent
    // rather than by nested containers.
    val data = remember(tree, expandedIds) {
        buildList {
            tree.roots.forEach { parent ->
                val children = tree.childrenOf(parent)
                add(PickerRow.Parent(category = parent, hasChildren = children.isNotEmpty()))
                if (parent.id in expandedIds) {
                    children.forEach { add(PickerRow.Child(category = it)) }
                }
            }
            add(PickerRow.AddNew)
        }
    }

    WrapContentRow(
        modifier = Modifier
            .padding(horizontal = 16.dp),
        horizontalMarginBetweenItems = 12.dp,
        verticalMarginBetweenRows = 12.dp,
        items = data
    ) {
        when (it) {
            is PickerRow.Parent -> {
                CategoryButton(
                    category = it.category,
                    selected = it.category.id == selectedCategory?.id,
                    hasChildren = it.hasChildren,
                    expanded = it.category.id in expandedIds,
                    onClick = {
                        onSelected(it.category)
                    },
                    onLongClick = {
                        onEditCategory(it.category)
                    },
                    onToggleExpand = {
                        onToggleExpanded(it.category.id)
                    },
                    onDeselect = {
                        onSelected(null)
                    }
                )
            }

            is PickerRow.Child -> {
                CategoryButton(
                    category = it.category,
                    selected = it.category.id == selectedCategory?.id,
                    indent = true,
                    onClick = {
                        onSelected(it.category)
                    },
                    onLongClick = {
                        onEditCategory(it.category)
                    },
                    onToggleExpand = { },
                    onDeselect = {
                        onSelected(null)
                    }
                )
            }

            is PickerRow.AddNew -> {
                AddNewButton {
                    showCategoryModal(null)
                }
            }
        }
    }
}

/**
 * One rendered row. [Child] is only ever produced for a category
 * [CategoryTree.childrenOf] returned, so a subcategory leaking into the top
 * level is impossible by construction rather than by filtering afterwards.
 */
private sealed interface PickerRow {
    data class Parent(
        val category: Category,
        val hasChildren: Boolean,
    ) : PickerRow

    data class Child(val category: Category) : PickerRow

    /** Not a category, so it carries no Category - hence no shared `category`. */
    data object AddNew : PickerRow
}

@ExperimentalFoundationApi
@Composable
private fun CategoryButton(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeselect: () -> Unit,
    onToggleExpand: () -> Unit,
    indent: Boolean = false,
    hasChildren: Boolean = false,
    expanded: Boolean = false,
) {
    val categoryColor = category.color.value.toComposeColor()

    val rFull = UI.shapes.rFull

    Row(
        modifier = Modifier
            .thenIf(indent) {
                // Children sit one level in. WrapContentRow is a flat flow, so
                // this indent is the only signal that the chip belongs to the
                // parent above it.
                padding(start = 20.dp)
            }
            .thenIf(selected) {
                drawColoredShadow(categoryColor)
            }
            .clip(UI.shapes.rFull)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = 2.dp,
                color = if (selected) UI.colors.pureInverse else UI.colors.medium,
                shape = UI.shapes.rFull
            )
            .thenIf(selected) {
                background(categoryColor, rFull)
            }
            .testTag("choose_category_button"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(if (selected) 12.dp else 8.dp))

        ItemIconSDefaultIcon(
            modifier = Modifier
                .background(categoryColor, CircleShape),
            iconName = category.icon?.id,
            defaultIcon = R.drawable.ic_custom_category_s,
            tint = findContrastTextColor(categoryColor)
        )

        Text(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .padding(
                    start = if (selected) 12.dp else 12.dp,
                    end = if (selected) 20.dp else 24.dp
                ),
            text = category.name.value,
            style = UI.typo.b2.style(
                color = if (selected) {
                    findContrastTextColor(categoryColor)
                } else {
                    UI.colors.pureInverse
                },
                fontWeight = FontWeight.SemiBold
            )
        )

        if (hasChildren) {
            // Expanding is a separate hit target from selecting: tapping the
            // chip body still picks the parent, which is what anyone not
            // drilling down expects, and it leaves long-press-to-edit exactly
            // where it was.
            Icon(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f)
                    .clip(UI.shapes.rFull)
                    .clickable(onClick = onToggleExpand)
                    .testTag("choose_category_expand_button"),
                painter = painterResource(id = R.drawable.ic_expand_more),
                contentDescription = if (expanded) {
                    stringResource(R.string.collapse_category, category.name.value)
                } else {
                    stringResource(R.string.expand_category, category.name.value)
                },
                tint = if (selected) {
                    findContrastTextColor(categoryColor)
                } else {
                    UI.colors.mediumInverse
                }
            )
        }

        if (selected) {
            val deselectBtnBackground = findContrastTextColor(categoryColor)
            IvyCircleButton(
                modifier = Modifier
                    .size(32.dp),
                icon = R.drawable.ic_remove,
                backgroundGradient = Gradient.solid(deselectBtnBackground),
                tint = findContrastTextColor(deselectBtnBackground)
            ) {
                onDeselect()
            }

            Spacer(Modifier.width(8.dp))
        }
    }
}

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun AddNewButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IvyBorderButton(
        modifier = modifier,
        text = stringResource(R.string.add_new),
        backgroundGradient = Gradient.solid(UI.colors.mediumInverse),
        iconStart = R.drawable.ic_plus,
        textStyle = UI.typo.b2.style(
            color = UI.colors.pureInverse,
            fontWeight = FontWeight.Bold
        ),
        iconTint = UI.colors.pureInverse,
        padding = 10.dp,
        onClick = onClick
    )
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun PreviewChooseCategoryModal() {
    IvyWalletPreview {
        val food = Category(
            name = NotBlankTrimmedString.unsafe("Food"),
            color = ColorInt(Ivy.toArgb()),
            icon = null,
            id = CategoryId(UUID.randomUUID()),
            orderNum = 0.0,
        )
        val categories = mutableListOf(
            food,
            Category(
                name = NotBlankTrimmedString.unsafe("Restaurants"),
                color = ColorInt(Orange.toArgb()),
                icon = null,
                id = CategoryId(UUID.randomUUID()),
                orderNum = 1.0,
                parentId = food.id,
            ),
            Category(
                name = NotBlankTrimmedString.unsafe("Second"),
                color = ColorInt(Orange.toArgb()),
                icon = null,
                id = CategoryId(UUID.randomUUID()),
                orderNum = 2.0,
            ),
            Category(
                name = NotBlankTrimmedString.unsafe("Third"),
                color = ColorInt(Red.toArgb()),
                icon = null,
                id = CategoryId(UUID.randomUUID()),
                orderNum = 3.0,
            ),
        )

        ChooseCategoryModal(
            visible = true,
            initialCategory = categories.first(),
            categories = categories,
            showCategoryModal = { },
            onCategoryChanged = { }
        ) {
        }
    }
}

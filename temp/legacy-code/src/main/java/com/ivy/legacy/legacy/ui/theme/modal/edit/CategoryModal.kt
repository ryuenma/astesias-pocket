package com.ivy.wallet.ui.theme.modal.edit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arrow.core.raise.either
import com.ivy.data.category.CategoryTree
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.utils.thenIf
import com.ivy.domain.legacy.ui.IvyColorPicker
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.utils.drawColoredShadow
import com.ivy.legacy.utils.hideKeyboard
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.legacy.utils.onScreenStart
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.ui.R
import com.ivy.wallet.domain.deprecated.logic.model.CreateCategoryData
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.ItemIconMDefaultIcon
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.components.IvyBorderButton
import com.ivy.wallet.ui.theme.components.IvyNameTextField
import com.ivy.wallet.ui.theme.components.WrapContentRow
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.toComposeColor
import com.ivy.wallet.ui.theme.dynamicContrast
import com.ivy.wallet.ui.theme.modal.ChooseIconModal
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalAddSave
import com.ivy.wallet.ui.theme.modal.ModalTitle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID

@Deprecated("Old design system. Use `:ivy-design` and Material3")
data class CategoryModalData(
    val category: Category?,
    val id: UUID = UUID.randomUUID(),
    val autoFocusKeyboard: Boolean = true,
)

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BoxWithConstraintsScope.CategoryModal(
    modal: CategoryModalData?,
    onCreateCategory: (CreateCategoryData) -> Unit,
    onEditCategory: (Category) -> Unit,
    /**
     * Every category in the app, so this one can be given a parent.
     *
     * Defaults to empty because the modal also renders in previews, where
     * there is nothing to nest under and the selector hides itself rather than
     * showing an inert control.
     */
    categories: ImmutableList<Category> = persistentListOf(),
    dismiss: () -> Unit,
) {
    val initialCategory = modal?.category
    var nameTextFieldValue by remember(modal) {
        mutableStateOf(selectEndTextFieldValue(initialCategory?.name?.value))
    }
    var color by remember(modal) {
        mutableStateOf(initialCategory?.color?.let { Color(it.value) } ?: Ivy)
    }
    var icon by remember(modal) {
        mutableStateOf(initialCategory?.icon)
    }
    var parentId by remember(modal) {
        mutableStateOf(initialCategory?.parentId)
    }
    var parentSelectorVisible by remember(modal) {
        mutableStateOf(false)
    }

    // Derived from parentId rather than from the picked Category, so the
    // selector keeps working after the parent's row is renamed underneath it.
    val parentCandidates = remember(categories, initialCategory) {
        val tree = CategoryTree.of(categories)
        // Roots only, and never anything inside this category's own subtree -
        // that is what makes self-parenting and descendant-parenting
        // impossible to choose rather than merely rejected on save.
        val candidates = if (initialCategory != null) {
            tree.parentCandidates(initialCategory)
        } else {
            tree.roots
        }
        candidates.toImmutableList()
    }
    val parentName = remember(categories, parentId) {
        categories.firstOrNull { it.id == parentId }?.name?.value
    }

    var chooseIconModalVisible by remember(modal) {
        mutableStateOf(false)
    }

    // The sibling-uniqueness rule, live. Two categories may share a name only
    // if they sit under different parents, so the scope of this check follows
    // `parentId`: picking a different parent re-evaluates it against that
    // parent's children. `initialCategory.id` is excluded so editing a category
    // without renaming it is not a collision with itself.
    //
    // The same rule is enforced in CategoryCreator against the database. This
    // copy exists purely so the user finds out while typing rather than after a
    // save that did nothing.
    val siblingTree = remember(categories) { CategoryTree.of(categories) }
    val nameIsTaken = remember(nameTextFieldValue.text, parentId, siblingTree, initialCategory) {
        siblingTree.hasSiblingNamed(
            name = nameTextFieldValue.text,
            parentId = parentId,
            excluding = initialCategory?.id
        )
    }

    IvyModal(
        id = modal?.id,
        visible = modal != null,
        dismiss = dismiss,
        PrimaryAction = {
            ModalAddSave(
                item = modal?.category,
                // A duplicate is blocked here rather than caught on save. The
                // inline message says why, and a Save that dismisses the modal
                // without storing anything reads as data loss.
                enabled = nameTextFieldValue.text.isNotNullOrBlank() && !nameIsTaken
            ) {
                if (initialCategory != null) {
                    val updatedCategory = either {
                        initialCategory.copy(
                            name = NotBlankTrimmedString.from(nameTextFieldValue.text.trim()).bind(),
                            color = ColorInt(color.toArgb()),
                            icon = icon,
                            // Clearing is a first-class outcome, not an
                            // omission: parentId is nullable precisely so a
                            // subcategory can be promoted back to the top.
                            parentId = parentId
                        )
                    }.getOrNull()

                    if (updatedCategory != null) {
                        onEditCategory(updatedCategory)
                    }
                } else {
                    onCreateCategory(
                        CreateCategoryData(
                            name = nameTextFieldValue.text.trim(),
                            color = color,
                            icon = icon?.id,
                            parentId = parentId
                        )
                    )
                }

                dismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(
            text = if (modal?.category != null) {
                stringResource(R.string.edit_category)
            } else {
                stringResource(
                    R.string.create_category
                )
            }
        )

        Spacer(Modifier.height(24.dp))

        // Column so the validation message sits directly under the name field
        // it is about. IconNameRow is a Row and cannot host it.
        Column {
            IconNameRow(
                hint = stringResource(R.string.category_name),
                defaultIcon = R.drawable.ic_custom_category_m,
                color = color,
                icon = icon?.id,

                autoFocusKeyboard = modal?.autoFocusKeyboard ?: true,

                nameTextFieldValue = nameTextFieldValue,
                setNameTextFieldValue = { nameTextFieldValue = it },
                showChooseIconModal = {
                    chooseIconModalVisible = true
                }
            )

            // Two wordings because they are different mistakes: the second one
            // tells the user the name is fine elsewhere in the tree, which is
            // the whole point of nesting and is otherwise baffling.
            if (nameIsTaken) {
                Text(
                    modifier = Modifier
                        .padding(start = 64.dp, end = 36.dp)
                        .testTag("modal_category_name_taken"),
                    text = stringResource(
                        if (parentId == null) {
                            R.string.category_name_taken
                        } else {
                            R.string.category_name_taken_sibling
                        }
                    ),
                    style = UI.typo.b2.style(
                        color = Red,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        ParentCategorySelector(
            candidates = parentCandidates,
            parentName = parentName,
            visible = parentSelectorVisible,
            toggleVisibility = { parentSelectorVisible = !parentSelectorVisible },
            onParentSelect = { parentId = it }
        )

        Spacer(Modifier.height(32.dp))

        IvyColorPicker(
            selectedColor = color,
            onColorSelected = { color = it }
        )

        Spacer(Modifier.height(48.dp))
    }

    ChooseIconModal(
        visible = chooseIconModalVisible,
        initialIcon = icon?.id ?: "category",
        color = color,
        dismiss = { chooseIconModalVisible = false }
    ) {
        icon = it?.let { iconId -> IconAsset.unsafe(iconId) }
    }
}

/** Chevron size, matching the picker's expand affordance. */
private val ParentChevronSize = 20.dp

/**
 * A chevron pointing down rotates half a turn to point up when expanded.
 *
 * PascalCase because detekt's TopLevelPropertyNaming asks for it, and a const
 * so MagicNumber's ignoreConstantDeclaration lets the literal through.
 */
private const val ExpandedRotationDegrees = 180f

/**
 * The parent chooser.
 *
 * Collapsed to a single row that states the current parent, because the modal
 * is already scrollable and a permanently expanded list of every category would
 * bury the name field. The row is hidden entirely when there is nothing to
 * choose, so a first-run install with no categories shows no dead control.
 */
@Composable
private fun ParentCategorySelector(
    candidates: ImmutableList<Category>,
    parentName: String?,
    visible: Boolean,
    toggleVisibility: () -> Unit,
    onParentSelect: (CategoryId?) -> Unit,
) {
    if (candidates.isEmpty()) return

    Column {
        Row(
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 12.dp)
                .clip(UI.shapes.r2)
                .clickable(onClick = toggleVisibility)
                .testTag("modal_parent_category_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.parent_category),
                style = UI.typo.b1.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Text(
                text = parentName ?: stringResource(R.string.no_parent_category),
                style = UI.typo.b1.style(
                    color = UI.colors.mediumInverse,
                    fontWeight = FontWeight.Medium
                )
            )

            Icon(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(ParentChevronSize)
                    .rotate(if (visible) ExpandedRotationDegrees else 0f),
                painter = painterResource(id = R.drawable.ic_expand_more),
                contentDescription = stringResource(R.string.parent_category),
                tint = UI.colors.mediumInverse
            )
        }

        if (visible) {
            WrapContentRow(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                items = candidates,
                horizontalMarginBetweenItems = 8.dp,
                verticalMarginBetweenRows = 8.dp
            ) { candidate ->
                ParentChip(
                    category = candidate,
                    selected = candidate.name.value == parentName,
                    onClick = { onParentSelect(candidate.id) }
                )
            }

            // Clearing is offered explicitly. Without it a subcategory could
            // be created but never promoted back to the top.
            IvyBorderButton(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 4.dp)
                    .testTag("modal_parent_category_none"),
                text = stringResource(R.string.no_parent_category),
                iconStart = R.drawable.ic_remove,
                padding = 10.dp,
                onClick = { onParentSelect(null) }
            )
        }
    }
}

@Composable
private fun ParentChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val categoryColor = category.color.value.toComposeColor()
    // Hoisted: UI is a @Composable property, so reading it inside a thenIf
    // lambda - which is not a composable scope - does not compile.
    val rFull = UI.shapes.rFull

    Row(
        modifier = Modifier
            .thenIf(selected) { drawColoredShadow(categoryColor) }
            .clip(rFull)
            .clickable(onClick = onClick)
            .border(
                width = 2.dp,
                color = if (selected) UI.colors.pureInverse else UI.colors.medium,
                shape = rFull
            )
            .thenIf(selected) { background(categoryColor, rFull) }
            .testTag("modal_parent_category_chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemIconSDefaultIcon(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(categoryColor, CircleShape),
            iconName = category.icon?.id,
            defaultIcon = R.drawable.ic_custom_category_s,
            tint = findContrastTextColor(categoryColor)
        )

        Text(
            modifier = Modifier.padding(
                start = 8.dp,
                end = if (selected) 16.dp else 12.dp,
                top = 10.dp,
                bottom = 10.dp
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
    }
}

@Composable
fun IconNameRow(
    hint: String,
    @DrawableRes defaultIcon: Int,
    color: Color,
    icon: String?,

    autoFocusKeyboard: Boolean,

    nameTextFieldValue: TextFieldValue,
    setNameTextFieldValue: (TextFieldValue) -> Unit,

    showChooseIconModal: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        val nameFocus = FocusRequester()

        onScreenStart {
            if (autoFocusKeyboard) {
                nameFocus.requestFocus()
            }
        }

        Spacer(Modifier.width(24.dp))

        ItemIconMDefaultIcon(
            modifier = Modifier
                .clip(CircleShape)
                .background(color, CircleShape)
                .clickable {
                    showChooseIconModal()
                }
                .testTag("modal_item_icon"),
            iconName = icon,
            tint = color.dynamicContrast(),
            defaultIcon = defaultIcon
        )

        val view = LocalView.current
        IvyNameTextField(
            modifier = Modifier
                .padding(start = 28.dp, end = 36.dp)
                .focusRequester(nameFocus),
            underlineModifier = Modifier.padding(start = 24.dp, end = 32.dp),
            value = nameTextFieldValue,
            hint = hint,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Text,
                autoCorrect = true
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    hideKeyboard(view)
                }
            ),
        ) { newValue ->
            setNameTextFieldValue(newValue)
        }
    }
}

@Preview
@Composable
private fun PreviewCategoryModal() {
    IvyWalletPreview {
        val food = Category(
            name = NotBlankTrimmedString.unsafe("Food"),
            color = ColorInt(Ivy.toArgb()),
            icon = null,
            orderNum = 0.0,
            id = CategoryId(UUID.randomUUID()),
        )
        val rent = Category(
            name = NotBlankTrimmedString.unsafe("Rent"),
            color = ColorInt(Orange.toArgb()),
            icon = null,
            orderNum = 1.0,
            id = CategoryId(UUID.randomUUID()),
        )

        CategoryModal(
            modal = CategoryModalData(null),
            categories = listOf(food, rent).toImmutableList(),
            onCreateCategory = { },
            onEditCategory = { }
        ) {
        }
    }
}

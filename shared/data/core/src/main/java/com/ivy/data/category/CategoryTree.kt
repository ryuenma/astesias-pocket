package com.ivy.data.category

import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import java.util.UUID

/**
 * The category hierarchy, shaped for the two levels the picker renders.
 *
 * [Category.parentId] carries no foreign key. That is a deliberate trade: a
 * foreign key would force the schema to pick a delete policy for the user's
 * spending history, and both options are wrong (cascade silently eats a
 * subtree, no-action strands children behind a screen that no longer exists).
 * Integrity belongs above the data layer, so the price is paid HERE, where
 * the hierarchy is flattened for display and money is attributed.
 *
 * Two things follow from "no foreign key", and both are load-bearing:
 *
 *  1. **Orphans are representable.** A child whose parent row is gone has a
 *     `parentId` that resolves to nothing. `CategoryRepository.findRoots()`
 *     would not return it, so a picker built straight off the roots query
 *     would make that category unselectable while its transactions keep
 *     existing. [of] promotes orphans back to the top level.
 *
 *  2. **Cycles are representable.** `parentId` has no constraint, so A can
 *     be B's parent and B's parent A. Every traversal here is guarded by a
 *     visited set; nothing in this class recurses without one.
 *
 * Display depth is capped at two levels because that is the product decision.
 * Anything deeper is promoted rather than hidden, so a third level can never
 * become a category the user cannot file a transaction under. Rollup
 * ([rollupIds]) is NOT capped: it always walks the whole subtree, so a
 * deeper-than-displayed chain still has its money counted at the top.
 */
class CategoryTree private constructor(
    /** Categories to render at the top level, in the caller's order. */
    val roots: List<Category>,
    private val childrenByParent: Map<CategoryId?, List<Category>>,
    /**
     * Every category indexed by its stored [Category.parentId], INCLUDING the
     * `null` key for top-level ones.
     *
     * Distinct from [childrenByParent], which is filtered to non-null parents
     * because it drives rendering. Sibling-uniqueness needs the reverse: a
     * `null` lookup here means "the top level", a real scope that two roots
     * are not allowed to share a name within.
     */
    private val allByParent: Map<CategoryId?, List<Category>>,
    private val rootIds: Set<CategoryId>,
    private val rollups: Map<CategoryId, Set<UUID>>,
    private val idsByValue: Map<UUID, CategoryId>,
) {

    /**
     * Direct children to render beneath [category].
     *
     * A child that was itself promoted to [roots] (a cycle member, or a
     * third level) is excluded, otherwise it would render twice: once as its
     * own row and once under its parent.
     */
    fun childrenOf(category: Category): List<Category> =
        childrenByParent[category.id].orEmpty().filter { it.id !in rootIds }

    fun hasChildren(category: Category): Boolean = childrenOf(category).isNotEmpty()

    /**
     * Every category id whose money belongs to [category]: its own id plus
     * its whole subtree.
     *
     * Falls back to the category alone when it is not in this tree. Callers
     * synthesise categories that never touched the database (the pie
     * chart's "account transfers" bucket), and those must keep matching
     * themselves rather than silently dropping to zero.
     */
    fun rollupIds(category: Category): Set<UUID> =
        rollups[category.id] ?: setOf(category.id.value)

    /**
     * [rollupIds] for a set of ids, as stored in a budget.
     *
     * Ids with no matching category pass through unchanged, which keeps a
     * budget pointing at a deleted category behaving exactly as it did
     * before subcategories existed.
     */
    fun rollupIdsOf(selectedIds: Collection<UUID>): Set<UUID> {
        if (selectedIds.isEmpty()) return emptySet()

        val ids = mutableSetOf<UUID>()
        for (selectedId in selectedIds) {
            val categoryId = idsByValue[selectedId]
            val rollup = categoryId?.let { rollups[it] }
            if (rollup != null) {
                ids += rollup
            } else {
                ids += selectedId
            }
        }
        return ids
    }

    /**
     * Whether [candidate] may become [category]'s parent.
     *
     * A null [candidate] means "top level", which is always allowed.
     *
     * [rollupIds] is the subtree INCLUDING [category] itself, so one lookup
     * rejects both forbidden cases: a category that is its own parent, and a
     * category filed under its own descendant. Both are storable (parentId has
     * no constraint) and both are what a parent selector can produce by
     * accident, because the selector is offered the user's own categories as
     * options.
     *
     * Falls back to "allowed" for a [category] that is not in this tree,
     * matching [rollupIds]: there is no subtree to be inside of.
     */
    fun canBeParentOf(candidate: CategoryId?, category: Category): Boolean =
        candidate == null || candidate.value !in rollups[category.id].orEmpty()

    /**
     * Whether some category under [parentId] is already called [name].
     *
     * This is the sibling-uniqueness rule: two categories may share a name as
     * long as they sit under DIFFERENT parents, which is the entire point of
     * having a hierarchy at all. A top-level name is unique among top-level
     * names; a name under "Food & Drinks" is unique among that parent's
     * children only.
     *
     * `null` [parentId] means "top level" and is a real scope, not a wildcard:
     * two roots cannot share a name either.
     *
     * [excluding] is the category being edited, so a category that keeps its
     * own name does not collide with itself. Passing the id being saved is what
     * makes "rename, keep the name" and "rename" behave differently.
     *
     * Siblinghood is decided on the stored [Category.parentId], NOT on what the
     * picker renders. An orphan and a third-level category are *displayed* at
     * the top level by [of], but they are not siblings of a root, and treating
     * them as such would make the rule depend on a display cap.
     *
     * Case-insensitive, because name-as-key is already the de-facto contract
     * three consumers rely on: CSV import matches existing categories by
     * `name.lowercase()`. Enforcing exact-case uniqueness here would let "Food"
     * and "food" coexist as siblings and hand that importer an ambiguous key
     * all over again.
     */
    fun hasSiblingNamed(
        name: String,
        parentId: CategoryId?,
        excluding: CategoryId? = null,
    ): Boolean {
        val wanted = name.trim()
        if (wanted.isEmpty()) return false
        return allByParent[parentId].orEmpty().any { sibling ->
            sibling.id != excluding && sibling.name.value.equals(wanted, ignoreCase = true)
        }
    }

    /**
     * The categories offered as parents when editing [category]: the roots,
     * minus everything that would make the hierarchy invalid.
     *
     * Roots only, because the picker renders two levels - offering a
     * subcategory as a parent would produce a third level that gets promoted
     * straight back to the top (see [of]).
     */
    fun parentCandidates(category: Category): List<Category> {
        val forbidden = rollups[category.id].orEmpty()
        return roots.filter { it.id.value !in forbidden }
    }

    /**
     * Whether a transaction filed under [transactionCategoryId] counts
     * towards [selected].
     *
     * A null [selected] means "unspecified category", and matches ONLY
     * transactions with no category. It must not roll up, or the
     * unspecified bucket would swallow every subcategory's money.
     */
    fun matches(transactionCategoryId: UUID?, selected: Category?): Boolean =
        if (selected == null) {
            transactionCategoryId == null
        } else {
            transactionCategoryId != null && rollupIds(selected).contains(transactionCategoryId)
        }

    companion object {
        /** Parents, plus their children: the two levels the picker shows. */
        private const val MAX_DISPLAY_DEPTH = 1

        fun of(categories: List<Category>): CategoryTree {
            val childrenByParent = categories
                .filter { it.parentId != null }
                .groupBy(Category::parentId)

            val roots = mutableListOf<Category>()
            val claimed = mutableSetOf<CategoryId>()

            // Walk the caller's order and promote whatever is still
            // unclaimed. Order matters and is load-bearing: it keeps the
            // sort the caller already applied (orderNum, or the
            // `sortCategoriesAscending` name sort) instead of overriding it
            // here. `claimed` is what makes a promotion a promotion rather
            // than a duplicate - a child is skipped once its parent has
            // claimed it.
            for (candidate in categories) {
                if (candidate.id in claimed) continue

                roots += candidate
                claimed += claimSubtree(candidate, childrenByParent)
            }

            return CategoryTree(
                roots = roots,
                childrenByParent = childrenByParent,
                allByParent = categories.groupBy(Category::parentId),
                rootIds = roots.mapTo(mutableSetOf(), Category::id),
                rollups = rollupsOf(categories, childrenByParent),
                idsByValue = categories.associate { it.id.value to it.id },
            )
        }

        /**
         * The ids at or below [root], to a depth of [MAX_DISPLAY_DEPTH] + 1.
         *
         * Stops descending past the displayed depth so a chain longer than
         * the UI does not get swallowed: its tail stays unclaimed and is
         * promoted to the top level by the next loop, where it is visible.
         */
        private fun claimSubtree(
            root: Category,
            childrenByParent: Map<CategoryId?, List<Category>>,
        ): Set<CategoryId> {
            val claimed = mutableSetOf<CategoryId>()
            var frontier = listOf(root.id)

            repeat(MAX_DISPLAY_DEPTH + 1) {
                val next = mutableListOf<CategoryId>()
                for (id in frontier) {
                    if (!claimed.add(id)) continue
                    next += childrenByParent[id].orEmpty().map(Category::id)
                }
                frontier = next
            }

            return claimed
        }

        /**
         * A rollup set per category, computed over the FULL subtree.
         *
         * Iterative with a visited set: [Category.parentId] has no foreign
         * key, so a cycle is storable and an unguarded walk here would
         * either overflow the stack or spin forever.
         */
        private fun rollupsOf(
            categories: List<Category>,
            childrenByParent: Map<CategoryId?, List<Category>>,
        ): Map<CategoryId, Set<UUID>> {
            val rollups = mutableMapOf<CategoryId, Set<UUID>>()

            for (category in categories) {
                val ids = linkedSetOf<UUID>()
                val queue = ArrayDeque<CategoryId>()
                queue.addLast(category.id)

                while (queue.isNotEmpty()) {
                    val id = queue.removeLast()
                    if (!ids.add(id.value)) continue // already seen: cycle guard
                    childrenByParent[id].orEmpty().forEach { queue.addLast(it.id) }
                }

                rollups[category.id] = ids
            }

            return rollups
        }
    }
}
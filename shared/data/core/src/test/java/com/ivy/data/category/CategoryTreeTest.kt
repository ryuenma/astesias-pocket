package com.ivy.data.category

import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.NotBlankTrimmedString
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.UUID

/**
 * The category tree is where the "no foreign key on parentId" trade-off is
 * actually paid. A subcategory is an ordinary row pointing at a parent id,
 * with nothing in the schema stopping that pointer from dangling (an
 * orphan, after the parent is deleted) or from looping (a cycle).
 *
 * These tests pin the three properties that fall out of that:
 *   - nothing the user can spend under becomes unselectable,
 *   - no walk loops forever,
 *   - a parent's rollup contains its children's money and nobody else's.
 */
class CategoryTreeTest {

    private fun category(
        name: String,
        parentId: CategoryId? = null,
        orderNum: Double = 0.0,
    ) = Category(
        id = CategoryId(UUID.randomUUID()),
        name = NotBlankTrimmedString.unsafe(name),
        color = ColorInt(0),
        icon = null,
        orderNum = orderNum,
        parentId = parentId,
    )

    private fun names(categories: List<Category>) = categories.map { it.name.value }

    private fun Category.children(tree: CategoryTree) = names(tree.childrenOf(this))

    @Test
    fun `flat list - every category is a root`() {
        val a = category("Food")
        val b = category("Rent")

        val tree = CategoryTree.of(listOf(a, b))

        tree.roots shouldBe listOf(a, b)
        tree.childrenOf(a) shouldBe emptyList()
    }

    @Test
    fun `subcategory is not a root - it nests under its parent`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)

        val tree = CategoryTree.of(listOf(food, restaurants))

        tree.roots shouldBe listOf(food)
        food.children(tree) shouldBe listOf("Restaurants")
    }

    /**
     * orderNum is GLOBAL - CategoryCreator hands out findMaxOrderNum() + 1 -
     * so a child created after its parent's sibling sorts *between* two
     * roots. Flattening the list into roots-then-children must not inherit
     * that interleaving, or a parent and its child end up on opposite sides
     * of the list.
     */
    @Test
    fun `global orderNum does not split a parent from its child`() {
        val food = category("Food", orderNum = 1.0)
        val rent = category("Rent", orderNum = 2.0)
        val restaurants = category("Restaurants", parentId = food.id, orderNum = 3.0)

        val tree = CategoryTree.of(listOf(food, rent, restaurants))

        // Rent's orderNum sits between the two, but Rent is still a root and
        // Restaurants is still Food's child.
        names(tree.roots) shouldBe listOf("Food", "Rent")
        food.children(tree) shouldBe listOf("Restaurants")
    }

    @Test
    fun `caller order is preserved - the sort the view model applied is not overridden`() {
        val zeta = category("Zeta")
        val alpha = category("Alpha")
        val alphaChild = category("Alpha child", parentId = alpha.id)

        val tree = CategoryTree.of(listOf(zeta, alpha, alphaChild))

        names(tree.roots) shouldBe listOf("Zeta", "Alpha")
    }

    @Test
    fun `parent with several children keeps them in caller order`() {
        val food = category("Food")
        val coffee = category("Coffee", parentId = food.id)
        val restaurants = category("Restaurants", parentId = food.id)

        val tree = CategoryTree.of(listOf(food, restaurants, coffee))

        food.children(tree) shouldBe listOf("Restaurants", "Coffee")
    }

    /**
     * The delete policy is "no action" on purpose (see CategoryEntity), so a
     * parent can be deleted while its children survive. Their parentId now
     * dangles. findRoots() would not return them, so a picker built straight
     * off that query would make them unselectable while their transactions
     * keep existing - spending history pointing at nothing reachable.
     */
    @Test
    fun `orphan - parent deleted - is promoted to a root rather than becoming unreachable`() {
        val orphan = category("Groceries", parentId = CategoryId(UUID.randomUUID()))
        val rent = category("Rent")

        val tree = CategoryTree.of(listOf(orphan, rent))

        names(tree.roots) shouldContainExactlyInAnyOrder listOf("Groceries", "Rent")
    }

    @Test
    fun `self-parenting category does not hang the tree`() {
        // Constructed by hand on purpose: nothing in the schema prevents it.
        val id = CategoryId(UUID.randomUUID())
        val selfish = Category(
            id = id,
            name = NotBlankTrimmedString.unsafe("Selfish"),
            color = ColorInt(0),
            icon = null,
            orderNum = 0.0,
            parentId = id,
        )

        val tree = CategoryTree.of(listOf(selfish))

        names(tree.roots) shouldBe listOf("Selfish")
        tree.childrenOf(selfish) shouldBe emptyList()
    }

    @Test
    fun `two-node cycle terminates and stays visible`() {
        val a = category("A")
        val b = Category(
            id = CategoryId(UUID.randomUUID()),
            name = NotBlankTrimmedString.unsafe("B"),
            color = ColorInt(0),
            icon = null,
            orderNum = 0.0,
            parentId = a.id,
        )
        val aCyclic = a.copy(parentId = b.id)

        val tree = CategoryTree.of(listOf(aCyclic, b))

        // Neither loops forever nor vanishes. A<->B has no true root, so the
        // first category in caller order becomes one and the other nests
        // under it - reachable exactly once, which is what "not hidden"
        // has to mean.
        names(tree.roots) shouldBe listOf("A")
        aCyclic.children(tree) shouldBe listOf("B")
        b.children(tree) shouldBe emptyList()

        val rendered = tree.roots + tree.roots.flatMap(tree::childrenOf)
        rendered.map { it.id }.toSet().size shouldBe 2
    }

    @Test
    fun `three-level chain - the tail is promoted, not hidden`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val pizza = category("Pizza", parentId = restaurants.id)

        val tree = CategoryTree.of(listOf(food, restaurants, pizza))

        // Food -> Restaurants is a legitimate level-1 pair and stays nested.
        // Pizza would be a THIRD level, which the picker does not render, so
        // it is promoted to the top rather than left unreachable: a category
        // nobody can select is worse than one shown at the wrong depth.
        names(tree.roots) shouldBe listOf("Food", "Pizza")
        food.children(tree) shouldBe listOf("Restaurants")
        restaurants.children(tree) shouldBe emptyList()

        // Rollup is not capped at the displayed depth: Pizza's money still
        // counts for its real parent even though the UI cannot nest that
        // deep.
        tree.rollupIds(food) shouldContainExactlyInAnyOrder listOf(
            food.id.value,
            restaurants.id.value,
            pizza.id.value,
        )
    }

    @Test
    fun `every category appears exactly once across the whole tree`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val rent = category("Rent")
        val orphan = category("Orphan", parentId = CategoryId(UUID.randomUUID()))

        val tree = CategoryTree.of(listOf(food, restaurants, rent, orphan))

        val rendered = tree.roots + tree.roots.flatMap(tree::childrenOf)

        rendered.map { it.id }.toSet().size shouldBe 4
    }

    // ---- Rollup: the money side ----

    @Test
    fun `parent rollup includes its own id and its children's`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val rent = category("Rent")

        val tree = CategoryTree.of(listOf(food, restaurants, rent))

        tree.rollupIds(food) shouldContainExactlyInAnyOrder listOf(
            food.id.value,
            restaurants.id.value,
        )
    }

    @Test
    fun `child rollup is just itself - a child's money is not double counted upward`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)

        val tree = CategoryTree.of(listOf(food, restaurants))

        tree.rollupIds(restaurants) shouldBe setOf(restaurants.id.value)
    }

    @Test
    fun `rollup does not leak into siblings`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val groceries = category("Groceries", parentId = food.id)
        val rent = category("Rent")

        val tree = CategoryTree.of(listOf(food, restaurants, groceries, rent))

        tree.rollupIds(restaurants).contains(rent.id.value) shouldBe false
        tree.rollupIds(restaurants).contains(groceries.id.value) shouldBe false
    }

    /**
     * The pie chart's "account transfers" bucket is a Category built in
     * memory that never touched the database. It is not in the tree, and it
     * must still match itself - a lookup miss must not silently zero it.
     */
    @Test
    fun `category outside the tree rolls up to itself`() {
        val tree = CategoryTree.of(listOf(category("Food")))
        val synthetic = category("Account transfers")

        tree.rollupIds(synthetic) shouldBe setOf(synthetic.id.value)
        tree.matches(synthetic.id.value, synthetic) shouldBe true
    }

    @Test
    fun `cycle rollup terminates and is bounded by the categories present`() {
        val a = category("A")
        val b = Category(
            id = CategoryId(UUID.randomUUID()),
            name = NotBlankTrimmedString.unsafe("B"),
            color = ColorInt(0),
            icon = null,
            orderNum = 0.0,
            parentId = a.id,
        )
        val aCyclic = a.copy(parentId = b.id)

        val tree = CategoryTree.of(listOf(aCyclic, b))

        tree.rollupIds(aCyclic) shouldContainExactlyInAnyOrder listOf(
            aCyclic.id.value,
            b.id.value,
        )
    }

    @Test
    fun `matches - a parent matches a child's transaction`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val tree = CategoryTree.of(listOf(food, restaurants))

        tree.matches(restaurants.id.value, food) shouldBe true
        tree.matches(food.id.value, food) shouldBe true
    }

    @Test
    fun `matches - a child does not match its parent's transaction`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val tree = CategoryTree.of(listOf(food, restaurants))

        tree.matches(food.id.value, restaurants) shouldBe false
    }

    /**
     * "Unspecified category" means transactions with NO category. It must not
     * roll up, or the unspecified bucket would absorb every subcategory's
     * money and the pie chart slices would no longer sum to the total.
     */
    @Test
    fun `matches - unspecified category matches only uncategorised transactions`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val tree = CategoryTree.of(listOf(food, restaurants))

        tree.matches(null, null) shouldBe true
        tree.matches(food.id.value, null) shouldBe false
        tree.matches(restaurants.id.value, null) shouldBe false
    }

    // ---- Parent selection ----

    /**
     * The shape a fresh install actually produces.
     *
     * Mirrors PreloadDataLogic.preloadCategories: ten seeded roots in list
     * order, then one child under Food & Drinks created afterwards. The
     * child's global orderNum is therefore HIGHER than every root's - which is
     * exactly the case the picker's expand chevron has to render, and the one
     * that cannot be reproduced by a hand-built two-element list.
     */
    @Test
    fun `seeded shape - one child under Food & Drinks, nested not promoted`() {
        val roots = listOf(
            category("Food & Drinks", orderNum = 0.0),
            category("Bills & Fees", orderNum = 1.0),
            category("Transport", orderNum = 2.0),
            category("Groceries", orderNum = 3.0),
            category("Entertainment", orderNum = 4.0),
        )
        val food = roots.first()
        val restaurant = category("Restaurant", parentId = food.id, orderNum = 5.0)

        val tree = CategoryTree.of(roots + restaurant)

        // Groceries stays a sibling: the seed deliberately nests Restaurant
        // rather than re-parenting an existing top-level category.
        names(tree.roots) shouldBe listOf(
            "Food & Drinks",
            "Bills & Fees",
            "Transport",
            "Groceries",
            "Entertainment",
        )
        food.children(tree) shouldBe listOf("Restaurant")

        // What the chevron needs: exactly one parent is expandable, and
        // expanding it reveals exactly one indented child.
        tree.roots.count { tree.hasChildren(it) } shouldBe 1
    }

    @Test
    fun `parent selector offers the roots, so a new category can be nested`() {
        val food = category("Food")
        val rent = category("Rent")
        val tree = CategoryTree.of(listOf(food, rent))

        // A category not yet in the tree has no subtree, so every root is
        // offered. This is the list the create path renders.
        tree.parentCandidates(category("Brand new")) shouldBe listOf(food, rent)
    }

    @Test
    fun `a category cannot be its own parent`() {
        val food = category("Food")
        val tree = CategoryTree.of(listOf(food))

        tree.canBeParentOf(food.id, food) shouldBe false
        // Clearing is always allowed - that is how a subcategory is promoted
        // back to the top.
        tree.canBeParentOf(null, food) shouldBe true
    }

    @Test
    fun `a category cannot be filed under its own descendant`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val pizza = category("Pizza", parentId = restaurants.id)

        val tree = CategoryTree.of(listOf(food, restaurants, pizza))

        // Food above its own grandchild would create a cycle, which is
        // storable because parentId has no constraint.
        tree.canBeParentOf(pizza.id, food) shouldBe false
        tree.canBeParentOf(restaurants.id, food) shouldBe false
    }

    @Test
    fun `parent candidates exclude the category itself and its whole subtree`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val pizza = category("Pizza", parentId = restaurants.id)
        val rent = category("Rent")

        val tree = CategoryTree.of(listOf(food, restaurants, pizza, rent))

        // Roots are the only candidates, so pizza - a third level - is not
        // offered at all, and food cannot be offered to itself. Rent remains.
        tree.parentCandidates(food) shouldBe listOf(rent)
    }

    /**
     * Editing a subcategory must still be able to keep or clear its parent,
     * otherwise the hierarchy can only ever be built, never undone.
     */
    @Test
    fun `a subcategory's own parent stays selectable while editing it`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val rent = category("Rent")

        val tree = CategoryTree.of(listOf(food, restaurants, rent))

        tree.canBeParentOf(food.id, restaurants) shouldBe true
        names(tree.parentCandidates(restaurants)) shouldBe listOf("Food", "Rent")
    }

    // ---- Budget filter ----

    @Test
    fun `budget on a parent covers its children`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val rent = category("Rent")
        val tree = CategoryTree.of(listOf(food, restaurants, rent))

        tree.rollupIdsOf(listOf(food.id.value)) shouldContainExactlyInAnyOrder listOf(
            food.id.value,
            restaurants.id.value,
        )
    }

    @Test
    fun `budget filter is empty - no rollup, so an empty filter stays empty`() {
        val food = category("Food")
        val restaurants = category("Restaurants", parentId = food.id)
        val tree = CategoryTree.of(listOf(food, restaurants))

        tree.rollupIdsOf(emptyList()) shouldBe emptySet()
    }

    /**
     * A budget can reference a category that was deleted. Its id must pass
     * through untouched rather than vanishing, so behaviour for a stale
     * budget is unchanged from before subcategories existed.
     */
    @Test
    fun `budget referencing a deleted category keeps its id`() {
        val food = category("Food")
        val deletedId = UUID.randomUUID()
        val tree = CategoryTree.of(listOf(food))

        tree.rollupIdsOf(listOf(deletedId)) shouldBe setOf(deletedId)
    }

    @Test
    fun `empty tree - rollup of an empty selection`() {
        val tree = CategoryTree.of(emptyList())

        tree.roots shouldBe emptyList()
        tree.rollupIdsOf(emptyList()) shouldBe emptySet()
    }

    /**
     * Sibling-name uniqueness, the rule three other subsystems assume.
     *
     * These are the assertions that were missing. Nothing anywhere checked a
     * name for uniqueness, so "QAChild" could be created twice under one parent
     * and the app carried on - while `getReplacementPairs` paired ids BY name,
     * the transaction delete-confirmation compares a TYPED NAME against the
     * category name, and CSV import resolves existing categories by
     * `name.lowercase()`. A duplicated name quietly breaks all three.
     */
    @Test
    fun `two top-level categories cannot share a name`() {
        val tree = CategoryTree.of(listOf(category("Food"), category("Rent")))

        tree.hasSiblingNamed(name = "Food", parentId = null) shouldBe true
        tree.hasSiblingNamed(name = "Rent", parentId = null) shouldBe true
    }

    /**
     * The point of the hierarchy: the SAME name is legal under two different
     * parents. Scoping uniqueness to the whole app would make nesting
     * pointless.
     */
    @Test
    fun `the same name under different parents is allowed`() {
        val food = category("Food")
        val bills = category("Bills")
        val tree = CategoryTree.of(
            listOf(
                food,
                bills,
                category("Coffee", parentId = food.id),
                category("Coffee", parentId = bills.id),
            )
        )

        tree.hasSiblingNamed(name = "Coffee", parentId = food.id) shouldBe true
        tree.hasSiblingNamed(name = "Coffee", parentId = bills.id) shouldBe true
    }

    @Test
    fun `a name free under one parent is not taken by another parent's child`() {
        val food = category("Food")
        val tree = CategoryTree.of(
            listOf(
                food,
                category("Coffee", parentId = food.id),
                category("Bills"),
            )
        )

        // "Coffee" exists, but only as Food's child, so it is free at the top.
        tree.hasSiblingNamed(name = "Coffee", parentId = null) shouldBe false
    }

    /**
     * The bug in miniature: a previous agent created "QAChild" twice under one
     * parent during on-device testing. This is the assertion that would have
     * caught it.
     */
    @Test
    fun `a duplicate child under one parent is detected`() {
        val food = category("Food")
        val tree = CategoryTree.of(
            listOf(
                food,
                category("QAChild", parentId = food.id),
                category("QAChild", parentId = food.id),
            )
        )

        tree.hasSiblingNamed(name = "QAChild", parentId = food.id) shouldBe true
    }

    /**
     * Editing must not reject a category for keeping its own name. Without
     * `excluding`, every save that does not rename anything would be refused -
     * which reads to the user as "this app will not let me edit my category".
     */
    @Test
    fun `a category does not collide with itself when excluded`() {
        val food = category("Food")
        val tree = CategoryTree.of(listOf(food))

        tree.hasSiblingNamed(
            name = "Food",
            parentId = null,
            excluding = food.id
        ) shouldBe false

        // The same lookup WITHOUT the exclusion is a collision, which is what
        // makes the exclusion load-bearing rather than decorative.
        tree.hasSiblingNamed(name = "Food", parentId = null) shouldBe true
    }

    /**
     * Moving a category must be checked against the NEW parent's children, not
     * the ones being left behind.
     */
    @Test
    fun `moving a category under a parent that already has that name collides`() {
        val food = category("Food")
        val bills = category("Bills")
        val coffee = category("Coffee")
        val tree = CategoryTree.of(
            listOf(
                food,
                bills,
                coffee,
                category("Coffee", parentId = food.id),
            )
        )

        // Legal where it is now.
        tree.hasSiblingNamed(
            name = coffee.name.value,
            parentId = coffee.parentId,
            excluding = coffee.id
        ) shouldBe false

        // Illegal the moment it moves under Food, which already has a "Coffee".
        tree.hasSiblingNamed(
            name = coffee.name.value,
            parentId = food.id,
            excluding = coffee.id
        ) shouldBe true
    }

    /**
     * CSV import resolves existing categories by `name.lowercase()`, so "Food"
     * and "food" are the SAME key to it. Exact-case uniqueness would let both
     * exist as siblings and hand that importer an ambiguous key again -
     * reintroducing the very defect this rule exists to close.
     */
    @Test
    fun `name matching ignores case and surrounding whitespace`() {
        val tree = CategoryTree.of(listOf(category("Food")))

        tree.hasSiblingNamed(name = "food", parentId = null) shouldBe true
        tree.hasSiblingNamed(name = "FOOD", parentId = null) shouldBe true
        tree.hasSiblingNamed(name = "  Food  ", parentId = null) shouldBe true
    }

    /**
     * A blank name is "not yet typed", not "a category called empty". Reporting
     * it as a duplicate would put the error on screen before the user has typed
     * anything.
     */
    @Test
    fun `a blank name is never a duplicate`() {
        val tree = CategoryTree.of(listOf(category("Food")))

        tree.hasSiblingNamed(name = "", parentId = null) shouldBe false
        tree.hasSiblingNamed(name = "   ", parentId = null) shouldBe false
    }

    /**
     * Siblinghood is decided on the stored parentId, not on what the picker
     * renders. An orphan is DISPLAYED as a root by [CategoryTree.of], but it is
     * not a sibling of one, and treating it as such would make the uniqueness
     * rule depend on a display depth cap.
     */
    @Test
    fun `an orphan displayed at the top level is not a root sibling`() {
        val goneParentId = CategoryId(UUID.randomUUID())
        val food = category("Food")
        val orphan = category("Food", parentId = goneParentId)
        val tree = CategoryTree.of(listOf(food, orphan))

        // The tree promotes the orphan so the user can still file under it.
        tree.roots.map { it.name.value } shouldBe listOf("Food", "Food")

        // But its stored parent is not null, so it is not a top-level collision
        // - it is scoped to the parent that no longer exists.
        tree.hasSiblingNamed(name = "Food", parentId = goneParentId) shouldBe true
    }

    @Test
    fun `empty tree - no name is taken`() {
        val tree = CategoryTree.of(emptyList())

        tree.hasSiblingNamed(name = "Food", parentId = null) shouldBe false
        tree.hasSiblingNamed(
            name = "Food",
            parentId = CategoryId(UUID.randomUUID())
        ) shouldBe false
    }
}

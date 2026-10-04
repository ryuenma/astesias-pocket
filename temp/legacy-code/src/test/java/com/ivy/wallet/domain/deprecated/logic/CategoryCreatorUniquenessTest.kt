package com.ivy.wallet.domain.deprecated.logic

import androidx.compose.ui.graphics.Color
import com.ivy.base.legacy.appContext
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.CategoryRepository
import com.ivy.wallet.domain.deprecated.logic.model.CreateCategoryData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

/**
 * The uniqueness rule is enforced HERE, in the domain layer, not only in the
 * modal.
 *
 * The modal applies the same rule live so the user sees it while typing, but a
 * modal is one caller. These four screens reach CategoryCreator with no
 * validation of their own - Categories, Edit Transaction, Edit Planned Payment
 * and Transactions - and before this, all five paths would happily store "Food"
 * twice. Enforcing it at the single choke point every save goes through is what
 * makes the rule true regardless of which screen the user came from.
 */
class CategoryCreatorUniquenessTest {

    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)

    private val creator = CategoryCreator(categoryRepository = categoryRepository)

    private fun category(
        name: String,
        parentId: CategoryId? = null,
        id: CategoryId = CategoryId(UUID.randomUUID()),
    ) = Category(
        id = id,
        name = NotBlankTrimmedString.unsafe(name),
        color = ColorInt(0),
        icon = null,
        orderNum = 0.0,
        parentId = parentId,
    )

    private fun data(name: String, parentId: CategoryId? = null) = CreateCategoryData(
        name = name,
        color = Color.Red,
        icon = null,
        parentId = parentId
    )

    /**
     * A repository that answers findAll() with what has been saved so far, so
     * the second write in a test sees the first one - the same thing the real
     * memoised repository does within a session.
     *
     * [seed] pre-loads rows that already exist. Seeding through this helper
     * rather than re-stubbing `save` afterwards matters: a later
     * `coEvery { save(any()) }` REPLACES the accumulating answer, and the
     * uniqueness check - which reads findAll() - would then see an empty
     * database and happily accept every duplicate.
     */
    private fun repositoryRememberingSaves(seed: List<Category> = emptyList()) {
        val saved = seed.toMutableList()
        coEvery { categoryRepository.findAll() } answers { saved.toList() }
        // `firstArg<CategoryId>()` cannot be used here: CategoryId is a
        // @JvmInline value class, so MockK hands back the boxed UUID and the
        // cast throws ClassCastException. Accept whichever form arrives.
        coEvery { categoryRepository.findById(any()) } answers {
            val raw = invocation.args[0]
            val wanted = if (raw is CategoryId) raw.value else raw as UUID
            saved.firstOrNull { it.id.value == wanted }
        }
        coEvery { categoryRepository.save(any()) } coAnswers {
            val value = firstArg<Category>()
            val existing = saved.indexOfFirst { it.id == value.id }
            if (existing > -1) saved[existing] = value else saved.add(value)
        }
    }

    /** Every category the repository was actually asked to store. */
    private fun savedNames(): List<String> {
        val saved = mutableListOf<Category>()
        coVerify { categoryRepository.save(capture(saved)) }
        return saved.map { it.name.value }
    }

    @Test
    fun `creating a duplicate top-level name is rejected and stores nothing`() = runTest {
        repositoryRememberingSaves()
        creator.createCategory(data("Food")) {}

        val result = creator.createCategory(data("Food")) {}

        result shouldBe CategorySaveResult.Rejected.DuplicateName
        // The load-bearing assertion: not merely "reported", but nothing written.
        savedNames() shouldBe listOf("Food")
    }

    @Test
    fun `creating a duplicate child under one parent is rejected`() = runTest {
        val food = category("Food")
        repositoryRememberingSaves(seed = listOf(food, category("QAChild", parentId = food.id)))

        val result = creator.createCategory(data("QAChild", parentId = food.id)) {}

        result shouldBe CategorySaveResult.Rejected.DuplicateName
        // The seed was loaded, not written, so `save` must never be reached:
        // rejecting without storing is the whole point.
        coVerify(exactly = 0) { categoryRepository.save(any()) }
    }

    /**
     * The hierarchy has to stay useful: the same name under a DIFFERENT parent
     * is the whole reason subcategories exist.
     */
    @Test
    fun `the same name under different parents is accepted`() = runTest {
        val food = category("Food")
        val bills = category("Bills")
        repositoryRememberingSaves(seed = listOf(food, bills))

        val underFood = creator.createCategory(data("Coffee", parentId = food.id)) {}
        val underBills = creator.createCategory(data("Coffee", parentId = bills.id)) {}

        underFood.shouldNotBeNull()
        (underFood is CategorySaveResult.Saved) shouldBe true
        (underBills is CategorySaveResult.Saved) shouldBe true
    }

    /**
     * Editing a category WITHOUT renaming it must not be rejected as a
     * duplicate of itself. Getting this wrong makes the app look broken: every
     * save of an untouched name would be refused.
     */
    @Test
    fun `editing a category without renaming it succeeds`() = runTest {
        val food = category("Food")
        repositoryRememberingSaves(seed = listOf(food))

        val result = creator.editCategory(food.copy(color = ColorInt(99))) {}

        (result is CategorySaveResult.Saved) shouldBe true
    }

    @Test
    fun `renaming a category onto a sibling's name is rejected`() = runTest {
        val food = category("Food")
        val rent = category("Rent")
        repositoryRememberingSaves(seed = listOf(food, rent))

        val result = creator.editCategory(
            rent.copy(name = NotBlankTrimmedString.unsafe("Food"))
        ) {}

        result shouldBe CategorySaveResult.Rejected.DuplicateName
    }

    /**
     * Moving a category must be checked against the NEW parent's children.
     * Checking against the old ones would let "Food" land beside another "Food"
     * under the parent the user just picked.
     */
    @Test
    fun `moving a category onto a new parent's existing child name is rejected`() = runTest {
        val food = category("Food")
        val bills = category("Bills")
        val coffee = category("Coffee")
        repositoryRememberingSaves(
            seed = listOf(food, bills, coffee, category("Coffee", parentId = food.id))
        )

        val result = creator.editCategory(
            coffee.copy(parentId = food.id) // Food already has a "Coffee"
        ) {}

        result shouldBe CategorySaveResult.Rejected.DuplicateName
    }

    /**
     * The pre-existing cycle guard must survive the change: a category filed
     * under its own descendant is still demoted to a root rather than stored as
     * a loop.
     */
    @Test
    fun `a category filed under its own descendant is still demoted to a root`() = runTest {
        val parent = category("Parent")
        val child = category("Child", parentId = parent.id)
        repositoryRememberingSaves(seed = listOf(parent, child))

        val result = creator.editCategory(parent.copy(parentId = child.id)) {}

        (result is CategorySaveResult.Saved) shouldBe true
        (result as CategorySaveResult.Saved).category.parentId shouldBe null
    }

    @Test
    fun `a blank name is rejected without touching the repository`() = runTest {
        repositoryRememberingSaves()

        creator.createCategory(data("   ")) {} shouldBe CategorySaveResult.Rejected.BlankName

        coVerify(exactly = 0) { categoryRepository.save(any()) }
    }

    init {
        // CreateCategoryData carries a Compose Color, and the module under test
        // pulls colours from resources. Nothing here reads one, but the mock
        // has to exist for the class to load.
        appContext = mockk(relaxed = true)
    }
}
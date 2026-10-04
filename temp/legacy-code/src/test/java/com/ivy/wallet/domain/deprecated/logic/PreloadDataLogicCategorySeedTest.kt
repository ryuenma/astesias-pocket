package com.ivy.wallet.domain.deprecated.logic

import android.content.Context
import com.ivy.base.legacy.appContext
import com.ivy.data.model.Category
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.CurrencyRepository
import com.ivy.ui.R
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Why the seed needs its own test.
 *
 * The subcategory feature is fully built and was still completely unreachable:
 * the picker had a chevron that never had anything to expand, because nothing
 * in the app could ever produce a category with a parent. Seed data is the one
 * place a fresh install gets a subcategory for free, so "does this seed
 * actually produce a nested pair" is a behavioural question, not a detail.
 *
 * It is also the easiest place to silently break a category that already
 * exists. Nesting the seeded "Groceries" under "Food & Drinks" would look
 * right in a screenshot and would re-parent a category the user already has
 * transactions against, so the sibling assertions below are load-bearing.
 */
class PreloadDataLogicCategorySeedTest {

    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val currencyRepository = mockk<CurrencyRepository>(relaxed = true)

    private lateinit var logic: PreloadDataLogic

    /**
     * Names are matched by localised string, so the context has to answer with
     * something. Returning the resource NAME keeps the test about structure
     * (who is whose child) rather than about English or Indonesian wording.
     */
    @Before
    fun setup() {
        val context = mockk<Context>()
        coEvery { context.getString(any(), *anyVararg()) } answers {
            "res_${firstArg<Int>()}"
        }
        appContext = context

        logic = PreloadDataLogic(
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
        )
    }

    /**
     * preloadSubcategories re-reads the repository to find the parent it was
     * just handed, so a fake that answers findAll() with what has been saved so
     * far is needed to make the seeding loop terminate on real data.
     */
    private fun repositoryRememberingSaves() {
        val saved = mutableListOf<Category>()

        coEvery { categoryRepository.findAll() } answers { saved.toList() }
        coEvery { categoryRepository.save(any()) } coAnswers {
            saved += firstArg<Category>()
        }
    }

    @Test
    fun `a fresh install seeds at least one real parent-child pair`() = runTest {
        repositoryRememberingSaves()

        logic.preloadCategories()

        val all = mutableListOf<Category>()
        coVerify { categoryRepository.save(capture(all)) }

        val food = all.firstOrNull { it.name.value == "res_${R.string.food_drinks}" }
        val restaurant = all.firstOrNull { it.name.value == "res_${R.string.restaurant}" }

        food.shouldNotBeNull()
        restaurant.shouldNotBeNull()

        // The whole point: a category that points at another one.
        restaurant.parentId shouldBe food.id
    }

    @Test
    fun `seeded Groceries stays top level and is not re-parented`() = runTest {
        repositoryRememberingSaves()

        logic.preloadCategories()

        val all = mutableListOf<Category>()
        coVerify { categoryRepository.save(capture(all)) }

        val groceries = all.firstOrNull { it.name.value == "res_${R.string.groceries}" }
        groceries.shouldNotBeNull()

        // "Groceries" is the obvious-looking child of Food & Drinks and the
        // wrong one: it is already a top-level category in its own right.
        groceries.parentId shouldBe null
    }

    @Test
    fun `every other seeded root also stays top level`() = runTest {
        repositoryRememberingSaves()

        logic.preloadCategories()

        val all = mutableListOf<Category>()
        coVerify { categoryRepository.save(capture(all)) }

        // Exactly one category in the seed is a child. If this count drifts,
        // the hierarchy is growing without anyone deciding what it should be.
        all.count { it.parentId != null } shouldBe 1

        val roots = all.filter { it.parentId == null }
        roots.map { it.name.value } shouldContainExactlyInAnyOrder listOf(
            "res_${R.string.food_drinks}",
            "res_${R.string.bills_fees}",
            "res_${R.string.transport}",
            "res_${R.string.groceries}",
            "res_${R.string.entertainment}",
            "res_${R.string.shopping}",
            "res_${R.string.gifts}",
            "res_${R.string.health}",
            "res_${R.string.investments}",
            "res_${R.string.loans}",
        )
    }

    /**
     * A seed must never store a parentId that resolves to nothing. If the
     * parent row cannot be found the child is skipped, not orphaned: the tree
     * would promote an orphan back to the top, so an orphan here would show up
     * as a second top-level "Restaurant" and the feature would look broken
     * rather than absent.
     */
    @Test
    fun `a child whose parent is missing is skipped rather than orphaned`() = runTest {
        coEvery { categoryRepository.findAll() } returns emptyList()

        logic.preloadCategories()

        val all = mutableListOf<Category>()
        coVerify { categoryRepository.save(capture(all)) }

        all.count { it.name.value == "res_${R.string.restaurant}" } shouldBe 0
    }
}

package com.ivy.data.dao

import com.ivy.data.db.dao.fake.FakeCategoryDao
import com.ivy.data.db.entity.CategoryEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FakeCategoryDaoTest {

    private lateinit var dao: FakeCategoryDao

    @Before
    fun setup() {
        dao = FakeCategoryDao()
    }

    @Test
    fun `find all - not deleted`() = runTest {
        // given
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val categories = listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            ),
            CategoryEntity(
                name = "Fun",
                color = 42,
                icon = null,
                orderNum = 2.0,
                isDeleted = true,
                id = id2
            )
        )

        // when
        dao.saveMany(categories)
        val res = dao.findAll(false)

        // then
        res shouldBe listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            )
        )
    }

    @Test
    fun deleted() = runTest {
        // given
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val categories = listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            ),
            CategoryEntity(
                name = "Fun",
                color = 42,
                icon = null,
                orderNum = 2.0,
                isDeleted = true,
                id = id2
            )
        )

        // when
        dao.saveMany(categories)
        val res = dao.findAll(true)

        // then
        res shouldBe listOf(
            CategoryEntity(
                name = "Fun",
                color = 42,
                icon = null,
                orderNum = 2.0,
                isDeleted = true,
                id = id2
            )
        )
    }

    @Test
    fun `empty list`() = runTest {
        // given
        val categories = emptyList<CategoryEntity>()

        // when
        dao.saveMany(categories)
        val res = dao.findAll(false)

        // then
        res shouldBe emptyList()
    }

    @Test
    fun `find by id - existing id`() = runTest {
        // given
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val category2 = CategoryEntity(
            name = "Fun",
            color = 42,
            icon = null,
            orderNum = 2.0,
            isDeleted = true,
            id = id2
        )
        val categories = listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            ),
            category2
        )

        // when
        dao.saveMany(categories)
        val res = dao.findById(id2)

        // then
        res shouldBe category2
    }

    @Test
    fun `find by id - not existing id`() = runTest {
        // given
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val categories = listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            ),
            CategoryEntity(
                name = "Fun",
                color = 42,
                icon = null,
                orderNum = 2.0,
                isDeleted = true,
                id = id2
            )
        )

        // when
        dao.saveMany(categories)
        val res = dao.findById(UUID.randomUUID())

        // then
        res shouldBe null
    }

    @Test
    fun `find max order num of categories`() = runTest {
        // given
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val categories = listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            ),
            CategoryEntity(
                name = "Fun",
                color = 42,
                icon = null,
                orderNum = 2.0,
                isDeleted = true,
                id = id2
            )
        )

        // when
        dao.saveMany(categories)
        val res = dao.findMaxOrderNum()

        // then
        res shouldBe 2.0
    }

    @Test
    fun `find max order num of empty list`() = runTest {
        // given
        val categories = emptyList<CategoryEntity>()

        // when
        dao.saveMany(categories)
        val res = dao.findMaxOrderNum()

        // then
        res shouldBe null
    }

    @Test
    fun `save - create new`() = runTest {
        // given
        val id = UUID.randomUUID()
        val category = CategoryEntity(
            name = "Home",
            color = 42,
            icon = null,
            orderNum = 1.0,
            isDeleted = false,
            id = id
        )

        // when
        dao.save(category)
        val res = dao.findById(id)

        // then
        res shouldBe category
    }

    @Test
    fun `save - update existing`() = runTest {
        // given
        val id = UUID.randomUUID()
        val category = CategoryEntity(
            name = "Home",
            color = 42,
            icon = null,
            orderNum = 1.0,
            isDeleted = false,
            id = id
        )
        val updated = category.copy(name = "My Home")

        // when
        dao.save(category)
        dao.save(updated)
        val res = dao.findAll(deleted = false)

        // then
        res shouldBe listOf(updated)
    }

    @Test
    fun `save many`() = runTest {
        // given
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val categories = listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            ),
            CategoryEntity(
                name = "Fun",
                color = 42,
                icon = null,
                orderNum = 2.0,
                isDeleted = false,
                id = id2
            )
        )

        // when
        dao.saveMany(categories)
        val res = dao.findAll(false)

        // then
        res shouldBe categories
    }

    @Test
    fun `delete by id`() = runTest {
        // given
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val categories = listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            ),
            CategoryEntity(
                name = "Fun",
                color = 42,
                icon = null,
                orderNum = 2.0,
                isDeleted = false,
                id = id2
            )
        )

        // when
        dao.saveMany(categories)
        dao.deleteById(id2)
        val res = dao.findAll(false)

        // then
        res shouldBe listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            )
        )
    }

    @Test
    fun `find roots - only categories without a parent`() = runTest {
        // given
        val rootId = UUID.randomUUID()
        val subId = UUID.randomUUID()
        val categories = listOf(
            CategoryEntity(
                name = "Food",
                color = 42,
                icon = null,
                orderNum = 1.0,
                parentId = null,
                isDeleted = false,
                id = rootId
            ),
            CategoryEntity(
                name = "Restaurants",
                color = 42,
                icon = null,
                orderNum = 2.0,
                parentId = rootId,
                isDeleted = false,
                id = subId
            )
        )

        // when
        dao.saveMany(categories)
        val res = dao.findRoots(false)

        // then
        res.map { it.id } shouldBe listOf(rootId)
    }

    @Test
    fun `find roots - ordered by order num`() = runTest {
        // given
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        dao.saveMany(
            listOf(
                CategoryEntity(
                    name = "Second",
                    color = 42,
                    icon = null,
                    orderNum = 2.0,
                    parentId = null,
                    isDeleted = false,
                    id = second
                ),
                CategoryEntity(
                    name = "First",
                    color = 42,
                    icon = null,
                    orderNum = 1.0,
                    parentId = null,
                    isDeleted = false,
                    id = first
                )
            )
        )

        // when
        val res = dao.findRoots(false)

        // then
        res.map { it.id } shouldBe listOf(first, second)
    }

    @Test
    fun `find subcategories - direct children only, ordered`() = runTest {
        // given
        val rootId = UUID.randomUUID()
        val childA = UUID.randomUUID()
        val childB = UUID.randomUUID()
        val grandChild = UUID.randomUUID()
        val otherRoot = UUID.randomUUID()
        dao.saveMany(
            listOf(
                CategoryEntity(
                    name = "Food",
                    color = 42,
                    icon = null,
                    orderNum = 1.0,
                    parentId = null,
                    isDeleted = false,
                    id = rootId
                ),
                CategoryEntity(
                    name = "Restaurants",
                    color = 42,
                    icon = null,
                    orderNum = 3.0,
                    parentId = rootId,
                    isDeleted = false,
                    id = childB
                ),
                CategoryEntity(
                    name = "Groceries",
                    color = 42,
                    icon = null,
                    orderNum = 2.0,
                    parentId = rootId,
                    isDeleted = false,
                    id = childA
                ),
                CategoryEntity(
                    name = "Pizza",
                    color = 42,
                    icon = null,
                    orderNum = 4.0,
                    parentId = childA,
                    isDeleted = false,
                    id = grandChild
                ),
                CategoryEntity(
                    name = "Rent",
                    color = 42,
                    icon = null,
                    orderNum = 5.0,
                    parentId = null,
                    isDeleted = false,
                    id = otherRoot
                )
            )
        )

        // when
        val res = dao.findSubcategories(rootId, false)

        // then
        res.map { it.id } shouldBe listOf(childA, childB)
    }

    @Test
    fun `find subcategories - deleted children are excluded`() = runTest {
        // given
        val rootId = UUID.randomUUID()
        val kept = UUID.randomUUID()
        val deleted = UUID.randomUUID()
        dao.saveMany(
            listOf(
                CategoryEntity(
                    name = "Food",
                    color = 42,
                    icon = null,
                    orderNum = 1.0,
                    parentId = null,
                    isDeleted = false,
                    id = rootId
                ),
                CategoryEntity(
                    name = "Groceries",
                    color = 42,
                    icon = null,
                    orderNum = 2.0,
                    parentId = rootId,
                    isDeleted = false,
                    id = kept
                ),
                CategoryEntity(
                    name = "Old",
                    color = 42,
                    icon = null,
                    orderNum = 3.0,
                    parentId = rootId,
                    isDeleted = true,
                    id = deleted
                )
            )
        )

        // when
        val notDeleted = dao.findSubcategories(rootId, false)
        val deletedChildren = dao.findSubcategories(rootId, true)

        // then
        notDeleted.map { it.id } shouldBe listOf(kept)
        deletedChildren.map { it.id } shouldBe listOf(deleted)
    }

    @Test
    fun `find subcategories - leaf has none`() = runTest {
        // given
        val leafId = UUID.randomUUID()
        dao.save(
            CategoryEntity(
                name = "Groceries",
                color = 42,
                icon = null,
                orderNum = 1.0,
                parentId = null,
                isDeleted = false,
                id = leafId
            )
        )

        // when
        val res = dao.findSubcategories(leafId, false)

        // then
        res shouldBe emptyList()
    }

    @Test
    fun `save - reparenting a category updates it in place`() = runTest {
        // given
        val id = UUID.randomUUID()
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val category = CategoryEntity(
            name = "Groceries",
            color = 42,
            icon = null,
            orderNum = 1.0,
            parentId = from,
            isDeleted = false,
            id = id
        )
        dao.save(category)

        // when
        dao.save(category.copy(parentId = to))
        val res = dao.findById(id)

        // then
        res?.parentId shouldBe to
        res shouldBe category.copy(parentId = to)
    }

    @Test
    fun `delete all`() = runTest {
        // given
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val categories = listOf(
            CategoryEntity(
                name = "Home",
                color = 42,
                icon = null,
                orderNum = 1.0,
                isDeleted = false,
                id = id1
            ),
            CategoryEntity(
                name = "Fun",
                color = 42,
                icon = null,
                orderNum = 2.0,
                isDeleted = true,
                id = id2
            )
        )

        // when
        dao.saveMany(categories)
        dao.deleteAll()
        val notDeleted = dao.findAll(false)
        val deleted = dao.findAll(true)

        // then
        notDeleted shouldBe emptyList()
        deleted shouldBe emptyList()
    }
}

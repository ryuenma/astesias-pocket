package com.ivy.data.backup

import com.ivy.data.db.entity.CategoryEntity
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Guards the backwards-compatibility promise of a new nullable column.
 *
 * Every backup ever written by this app predates `CategoryEntity.parentId`, so
 * the serializer has to keep decoding those files. A field added without a
 * default - or with a non-null type - would throw on import and take the whole
 * categories list down with it, which is exactly the kind of failure a user
 * only discovers when restoring the one backup they had.
 */
class CategoryBackupCompatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `decodes a pre-subcategory backup without a parentId key`() {
        // given a real backup from before subcategories existed
        val backup = File("src/androidTest/assets/backups/450-150.zip")
        val raw = ZipFile(backup).use { zip ->
            val entry: java.util.zip.ZipEntry = zip.entries().toList().first()
            zip.getInputStream(entry).readBytes()
        }
        val content = String(raw, Charsets.UTF_16)

        // when
        val data = json.decodeFromString<IvyWalletCompleteData>(content)

        // then the categories survive, and every one reads as top-level
        data.categories.shouldNotBeEmpty()
        data.categories.forEach { category ->
            category.parentId shouldBe null
        }
    }

    @Test
    fun `round-trips a parentId through a backup`() {
        // given
        val parentId = java.util.UUID.randomUUID()
        val withParent = CategoryEntity(
            name = "Groceries",
            color = 42,
            parentId = parentId,
        )
        val data = IvyWalletCompleteData(categories = listOf(withParent))

        // when
        val restored = json.decodeFromString<IvyWalletCompleteData>(
            json.encodeToString(IvyWalletCompleteData.serializer(), data)
        )

        // then
        restored.categories.single().parentId shouldBe parentId
    }
}
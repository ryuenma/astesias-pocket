package com.ivy.data.repository.mapper

import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.repository.CurrencyRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class IconRestoreMapperTest {

    @Test
    fun `AccountMapper and CategoryMapper preserve icons containing spaces or uppercase (#3177)`() = runTest {
        val currencyRepo = mockk<CurrencyRepository> {
            coEvery { getBaseCurrency() } returns AssetCode.unsafe("IDR")
        }
        val accountMapper = AccountMapper(currencyRepo)
        val categoryMapper = CategoryMapper()

        val accEntity = AccountEntity(
            name = "Bank BCA",
            currency = "IDR",
            color = 1,
            icon = "account balance",
            id = UUID.randomUUID()
        )
        val catEntity = CategoryEntity(
            name = "Food",
            color = 1,
            icon = "Food Drinks",
            id = UUID.randomUUID()
        )

        val domainAcc = with(accountMapper) { accEntity.toDomain().getOrNull() }
        val domainCat = with(categoryMapper) { catEntity.toDomain().getOrNull() }

        domainAcc?.icon shouldBe IconAsset.unsafe("account_balance")
        domainCat?.icon shouldBe IconAsset.unsafe("food_drinks")
    }
}

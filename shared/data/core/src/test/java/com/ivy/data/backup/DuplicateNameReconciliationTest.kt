package com.ivy.data.backup

import com.ivy.base.TestDispatchersProvider
import com.ivy.base.di.KotlinxSerializationModule
import com.ivy.data.DataObserver
import com.ivy.data.db.dao.fake.FakeAccountDao
import com.ivy.data.db.dao.fake.FakeBudgetDao
import com.ivy.data.db.dao.fake.FakeCategoryDao
import com.ivy.data.db.dao.fake.FakeLoanDao
import com.ivy.data.db.dao.fake.FakeLoanRecordDao
import com.ivy.data.db.dao.fake.FakePlannedPaymentDao
import com.ivy.data.db.dao.fake.FakeSettingsDao
import com.ivy.data.db.dao.fake.FakeTagAssociationDao
import com.ivy.data.db.dao.fake.FakeTagDao
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CurrencyRepository
import com.ivy.data.repository.fake.fakeRepositoryMemoFactory
import com.ivy.data.repository.mapper.AccountMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

/**
 * The backup importer reconciles ids BY NAME, so an unguarded duplicate name is
 * not cosmetic - it silently rewrites which category a transaction points at.
 *
 * `getReplacementPairs` used to group by name and keep only groups of exactly
 * two. That quietly did two different wrong things:
 *
 *  - a name appearing 3+ times was DROPPED with no signal, so the import
 *    reported success having reconciled nothing, and
 *  - a name appearing twice IN THE DATABASE (never in the backup) was treated
 *    as a legitimate pair and rewrote one existing id into the other across
 *    the entire backup - corrupting ids that were already correct.
 *
 * The rule now is "exactly one existing and exactly one backup", which is the
 * only case where the replacement has a single defensible answer. Everything
 * else is reported.
 *
 * The owner's live SO profile contained 'Contoh Transportasi' four times, so the
 * 3+ case is reachable, not theoretical.
 */
class DuplicateNameReconciliationTest {
    private fun category(name: String, id: UUID = UUID.randomUUID()) =
        CategoryEntity(name = name, color = 0, id = id)

    private fun account(name: String, id: UUID = UUID.randomUUID()) =
        AccountEntity(name = name, color = 0, id = id)

    private fun useCase(
        categoryDao: FakeCategoryDao,
        accountDao: FakeAccountDao,
        settingsDao: FakeSettingsDao = FakeSettingsDao(),
    ): BackupDataUseCase {
        val accountMapper = AccountMapper(
            CurrencyRepository(
                settingsDao = settingsDao,
                writeSettingsDao = settingsDao,
                dispatchersProvider = TestDispatchersProvider,
            )
        )
        return BackupDataUseCase(
            accountDao = accountDao,
            accountMapper = accountMapper,
            accountRepository = AccountRepository(
                accountDao = accountDao,
                writeAccountDao = accountDao,
                mapper = accountMapper,
                dispatchersProvider = TestDispatchersProvider,
                memoFactory = fakeRepositoryMemoFactory(),
            ),
            budgetDao = FakeBudgetDao(),
            categoryDao = categoryDao,
            loanRecordDao = FakeLoanRecordDao(),
            loanDao = FakeLoanDao(),
            plannedPaymentRuleDao = FakePlannedPaymentDao(),
            transactionDao = FakeTransactionDao(),
            transactionWriter = FakeTransactionDao(),
            settingsDao = settingsDao,
            categoryWriter = categoryDao,
            settingsWriter = settingsDao,
            budgetWriter = FakeBudgetDao(),
            loanWriter = FakeLoanDao(),
            loanRecordWriter = FakeLoanRecordDao(),
            plannedPaymentRuleWriter = FakePlannedPaymentDao(),
            context = mockk(relaxed = true),
            sharedPrefs = mockk(relaxed = true),
            json = KotlinxSerializationModule.provideJson(),
            dispatchersProvider = TestDispatchersProvider,
            fileSystem = mockk(relaxed = true),
            dataObserver = DataObserver(),
            tagsReader = FakeTagDao(),
            tagsWriter = FakeTagDao(),
            tagAssociationReader = FakeTagAssociationDao(),
            tagAssociationWriter = FakeTagAssociationDao(),
        )
    }

    private fun json(categories: List<CategoryEntity>, accounts: List<AccountEntity> = emptyList()): String {
        val data = IvyWalletCompleteData(categories = categories, accounts = accounts)
        return KotlinxSerializationModule.provideJson()
            .encodeToString(IvyWalletCompleteData.serializer(), data)
    }

    /**
     * The headline case from the owner's fixture: one name, four times in the
     * database. The old `size == 2` filter dropped this group in silence.
     */
    @Test
    fun `a name appearing four times in the database is reported not ignored`() = runTest {
        val existing = listOf(
            category("Contoh Transportasi"),
            category("Contoh Transportasi"),
            category("Contoh Transportasi"),
            category("Contoh Transportasi"),
        )
        val categoryDao = FakeCategoryDao()
        existing.forEach { categoryDao.save(it) }

        val result = useCase(categoryDao, FakeAccountDao()).importJson(
            // Exactly one backup row, with a name of its own. Had this repeated
            // "Groceries" four times it would be ambiguous too, and rightly so.
            json(listOf(category("Groceries")))
        )

        result.duplicateNameWarnings shouldContainExactly listOf("Contoh Transportasi")
        // The import itself still succeeded: refusing to restore somebody's
        // ledger over a naming collision is its own kind of data loss.
        result.errorMessage shouldBe null
    }

    @Test
    fun `a name appearing three times across both sides is reported`() = runTest {
        val categoryDao = FakeCategoryDao()
        listOf(category("Food"), category("Food")).forEach { categoryDao.save(it) }

        val result = useCase(categoryDao, FakeAccountDao()).importJson(
            json(listOf(category("Food")))
        )

        result.duplicateNameWarnings shouldContainExactly listOf("Food")
    }

    /**
     * The silent-corruption case: two rows in the DATABASE share a name and the
     * backup has nothing by that name. The old code treated the two existing
     * ids as a pair and rewrote one into the other across the whole backup.
     */
    @Test
    fun `a name duplicated only in the database is reported`() = runTest {
        val categoryDao = FakeCategoryDao()
        val first = category("Food")
        val second = category("Food")
        categoryDao.save(first)
        categoryDao.save(second)

        val untouched = category("Rent")
        val result = useCase(categoryDao, FakeAccountDao()).importJson(
            json(listOf(untouched))
        )

        result.duplicateNameWarnings shouldContainExactly listOf("Food")
    }

    /**
     * The rule still has to do its actual job: a clean one-to-one match is
     * reconciled, not reported. Without this the tests above would pass on an
     * implementation that simply never pairs anything.
     */
    @Test
    fun `one existing and one backup row reconcile silently`() = runTest {
        val existing = category("Food")
        val fromBackup = category("Food")
        val categoryDao = FakeCategoryDao()
        categoryDao.save(existing)

        val result = useCase(categoryDao, FakeAccountDao()).importJson(
            json(listOf(fromBackup))
        )

        result.duplicateNameWarnings.shouldBeEmpty()
        // The backup's id was rewritten to the existing one, which is the whole
        // point of the pass: no duplicate row is created.
        result.categoriesImported shouldBe 1
        categoryDao.findAll(deleted = false).size shouldBe 1
    }

    /**
     * A name only the backup has, appearing once, needs no reconciliation and
     * is not worth reporting - otherwise every fresh import into an empty
     * database would cry wolf about every category it creates.
     */
    @Test
    fun `a brand new name from the backup is not a warning`() = runTest {
        val categoryDao = FakeCategoryDao()
        categoryDao.save(category("Food"))

        val result = useCase(categoryDao, FakeAccountDao()).importJson(
            json(listOf(category("Brand New")))
        )

        result.duplicateNameWarnings.shouldBeEmpty()
    }

    /**
     * The old `size == 2` bug was not category-specific: accounts go through
     * the identical grouping, so the same silent drop applied to them.
     */
    @Test
    fun `duplicate account names are reported too`() = runTest {
        val accountDao = FakeAccountDao()
        listOf(account("Cash"), account("Cash"), account("Cash"))
            .forEach { accountDao.save(it) }
        val categoryDao = FakeCategoryDao()

        val result = useCase(categoryDao, accountDao).importJson(
            json(emptyList(), accounts = listOf(account("Bank")))
        )

        result.duplicateNameWarnings shouldContainExactly listOf("Cash")
    }

    /**
     * Two top-level categories legitimately sharing a name is now impossible
     * through the UI, but the database can still hold such rows from an old
     * backup. The importer must not refuse to restore over them - it reports
     * and proceeds, because a user's ledger outranks a tidy category list.
     */
    @Test
    fun `a pre-existing duplicate does not fail the import`() = runTest {
        val categoryDao = FakeCategoryDao()
        listOf(category("Food"), category("Food")).forEach { categoryDao.save(it) }

        val result = useCase(categoryDao, FakeAccountDao()).importJson(
            json(listOf(category("Transport"), category("Transport"), category("Transport")))
        )

        result.errorMessage shouldBe null
        result.isSuccess shouldBe true
    }
}
package com.ivy.data.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.base.TestDispatchersProvider
import com.ivy.base.di.KotlinxSerializationModule
import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.DataObserver
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.file.FileSystem
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CurrencyRepository
import com.ivy.data.repository.fake.fakeRepositoryMemoFactory
import com.ivy.data.repository.mapper.AccountMapper
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the REAL import path against the owner's actual Ivy Wallet backup.
 *
 * The backup is pushed to the app under test's own external files dir rather than
 * committed, so personal financial data never lands in version control. Push it
 * there first (the path is resolved at runtime and printed in the failure message):
 *
 * ```
 * adb push IvyWalletBackup_20261001-2259.zip \
 *   /sdcard/Android/data/astesias.pocket.debug/files/
 * ```
 *
 * Not `/sdcard/Download/`: on API 33+ that path is unreadable to the app under
 * test without a media/storage permission, so the import failed with
 * "EACCES (Permission denied)". The app-private external dir needs no permission
 * and is still per-app, so the backup is not committed either way.
 *
 * Why this test exists: Ivy Wallet hardcoded UTF-16 when reading backups and swallowed
 * every failure into an all-zero ImportResult, so a broken import looked exactly like
 * an empty wallet. This asserts on the imported counts *and* reads the rows back out
 * of Room, which a counter-only assertion would not catch.
 *
 * The owner's backup is UTF-16 **big** endian (BOM FE FF) - see BackupEncodingTest.
 */
@RunWith(AndroidJUnit4::class)
class RealBackupImportTest {

    private companion object {
        const val BACKUP_FILE = "IvyWalletBackup_20261001-2259.zip"

        // From profiling the owner's backup on 2026-10-01. Asserted rather than
        // assumed, so a changed fixture fails loudly instead of passing silently.
        const val EXPECTED_TRANSACTIONS = 6538
        const val EXPECTED_ACCOUNTS = 9
        const val EXPECTED_CATEGORIES = 31
    }

    private lateinit var db: IvyRoomDatabase
    private lateinit var useCase: BackupDataUseCase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IvyRoomDatabase::class.java).build()
        val appContext = InstrumentationRegistry.getInstrumentation().context
        val accountMapper = AccountMapper(
            currencyRepository = CurrencyRepository(
                settingsDao = db.settingsDao,
                writeSettingsDao = db.writeSettingsDao,
                dispatchersProvider = TestDispatchersProvider,
            )
        )
        useCase = BackupDataUseCase(
            accountDao = db.accountDao,
            budgetDao = db.budgetDao,
            categoryDao = db.categoryDao,
            loanRecordDao = db.loanRecordDao,
            loanDao = db.loanDao,
            plannedPaymentRuleDao = db.plannedPaymentRuleDao,
            settingsDao = db.settingsDao,
            transactionDao = db.transactionDao,
            transactionWriter = db.writeTransactionDao,
            sharedPrefs = SharedPrefs(appContext),
            accountRepository = AccountRepository(
                accountDao = db.accountDao,
                writeAccountDao = db.writeAccountDao,
                mapper = accountMapper,
                dispatchersProvider = TestDispatchersProvider,
                memoFactory = fakeRepositoryMemoFactory(),
            ),
            accountMapper = accountMapper,
            categoryWriter = db.writeCategoryDao,
            settingsWriter = db.writeSettingsDao,
            budgetWriter = db.writeBudgetDao,
            loanWriter = db.writeLoanDao,
            loanRecordWriter = db.writeLoanRecordDao,
            plannedPaymentRuleWriter = db.writePlannedPaymentRuleDao,
            context = appContext,
            json = KotlinxSerializationModule.provideJson(),
            dispatchersProvider = TestDispatchersProvider,
            fileSystem = FileSystem(appContext),
            dataObserver = DataObserver(),
            tagsReader = db.tagDao,
            tagAssociationReader = db.tagAssociationDao,
            tagsWriter = db.writeTagDao,
            tagAssociationWriter = db.writeTagAssociationDao
        )
    }

    @After
    fun closeDb() {
        db.close()
    }

    // Must return Unit explicitly. With `fun x() = runBlocking { ... }` the return type
    // is inferred from the block's last expression (`... shouldBe true` -> Boolean),
    // and JUnit rejects any @Test method that does not return void, so the method
    // is silently never executed.
    @Test
    fun importsTheOwnersRealBackup(): Unit = runBlocking {
        // Resolved at runtime, not hardcoded: instrumented code runs inside the
        // TARGET app's process (astesias.pocket.debug), so the only external files
        // dir it can read is its own. A hardcoded path that names the test APK's
        // dir exists but reads as absent.
        val backupDir = requireNotNull(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        ) { "target app has no external files dir" }
        val backup = File(backupDir, BACKUP_FILE)
        assertTrue(
            "Backup not found at ${backup.absolutePath} - run: " +
                "adb push $BACKUP_FILE ${backup.absolutePath}/",
            backup.exists()
        )
        assertTrue(
            "Backup at ${backup.absolutePath} is not readable by the test process",
            backup.canRead()
        )

        val result = useCase.importBackupFile(backup.toUri()) {}

        println(
            "REAL BACKUP IMPORT -> error=${result.errorMessage} " +
                "rows=${result.rowsFound} transactions=${result.transactionsImported} " +
                "accounts=${result.accountsImported} categories=${result.categoriesImported}"
        )

        // A silent failure previously produced all zeros with no reason given.
        result.errorMessage shouldBe null
        assertTrue(
            "Import reported no error but imported nothing",
            result.transactionsImported > 0
        )

        result.transactionsImported shouldBe EXPECTED_TRANSACTIONS
        result.accountsImported shouldBe EXPECTED_ACCOUNTS
        result.categoriesImported shouldBe EXPECTED_CATEGORIES

        // Prove the rows actually landed in Room, not merely in the counters.
        val stored = db.transactionDao.findAll().size
        println("rows read back from Room: $stored")
        stored shouldBe EXPECTED_TRANSACTIONS

        val accountNames = db.accountDao.findAll().map { it.name }
        println("accounts: $accountNames")
        // "Jenius Flexisave (DONT USE)" is a real account in the owner's data; the
        // owner chose to keep it on import rather than drop it.
        accountNames.any { it.contains("Flexisave") } shouldBe true
    }
}

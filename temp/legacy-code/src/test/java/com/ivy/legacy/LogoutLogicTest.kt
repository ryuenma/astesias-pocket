package com.ivy.legacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.DataObserver
import com.ivy.data.db.dao.read.DirectoryAccountDao
import com.ivy.data.db.dao.read.PendingTransferDao
import com.ivy.data.db.dao.read.UserDao
import com.ivy.data.db.dao.write.WriteBudgetDao
import com.ivy.data.db.dao.write.WriteLoanDao
import com.ivy.data.db.dao.write.WriteLoanRecordDao
import com.ivy.data.db.dao.write.WritePendingBillDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WriteSettingsDao
import com.ivy.data.db.dao.write.WriteTransferConfirmationDao
import com.ivy.data.db.dao.write.WriteTransferOutboxDao
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.ExchangeRatesRepository
import com.ivy.data.repository.TagRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.navigation.Navigation
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LogoutLogicTest {

    @Test
    fun `delete all data clears sync, transfer inbox, outbox, confirmations, and pending bills`() = runTest {
        val directoryAccountDao: DirectoryAccountDao = mockk(relaxed = true)
        val pendingTransferDao: PendingTransferDao = mockk(relaxed = true)
        val writeTransferOutboxDao: WriteTransferOutboxDao = mockk(relaxed = true)
        val writeTransferConfirmationDao: WriteTransferConfirmationDao = mockk(relaxed = true)
        val writePendingBillDao: WritePendingBillDao = mockk(relaxed = true)

        val logic = LogoutLogic(
            sharedPrefs = mockk<SharedPrefs>(relaxed = true),
            navigation = mockk<Navigation>(relaxed = true),
            dataObserver = mockk<DataObserver>(relaxed = true),
            dataStore = mockk<DataStore<Preferences>>(relaxed = true),
            accountRepository = mockk<AccountRepository>(relaxed = true),
            transactionRepository = mockk<TransactionRepository>(relaxed = true),
            categoryRepository = mockk<CategoryRepository>(relaxed = true),
            tagRepository = mockk<TagRepository>(relaxed = true),
            userDao = mockk<UserDao>(relaxed = true),
            writeSettingsDao = mockk<WriteSettingsDao>(relaxed = true),
            writePlannedPaymentRuleDao = mockk<WritePlannedPaymentRuleDao>(relaxed = true),
            writeBudgetDao = mockk<WriteBudgetDao>(relaxed = true),
            writeLoanDao = mockk<WriteLoanDao>(relaxed = true),
            writeLoanRecordDao = mockk<WriteLoanRecordDao>(relaxed = true),
            exchangeRatesRepository = mockk<ExchangeRatesRepository>(relaxed = true),
            directoryAccountDao = directoryAccountDao,
            pendingTransferDao = pendingTransferDao,
            writeTransferOutboxDao = writeTransferOutboxDao,
            writeTransferConfirmationDao = writeTransferConfirmationDao,
            writePendingBillDao = writePendingBillDao,
        )

        logic.logout()

        coVerify(exactly = 1) { directoryAccountDao.deleteAll() }
        coVerify(exactly = 1) { pendingTransferDao.deleteAll() }
        coVerify(exactly = 1) { writeTransferOutboxDao.deleteAll() }
        coVerify(exactly = 1) { writeTransferConfirmationDao.deleteAll() }
        coVerify(exactly = 1) { writePendingBillDao.deleteAll() }
    }
}

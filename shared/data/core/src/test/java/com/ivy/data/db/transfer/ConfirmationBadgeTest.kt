package com.ivy.data.db.transfer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.TransferConfirmationEntity
import com.ivy.data.db.entity.TransferOutboxEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The Settings badge and the answers list must describe the same set of rows.
 *
 * They did not. `findAllWithEvent` INNER JOINs the retained outbox event and
 * filters on `kind = 'TRANSFER_EVENT'`, so an answer whose event this device
 * cannot describe is never listed; `countUnacknowledged` counted every unread row
 * regardless. The badge therefore promised answers that opening the screen did not
 * show, and acknowledging cleared the count for them anyway.
 *
 * These run against a real database because the bug was in the SQL, and a mock
 * would only repeat whatever the query string was said to be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConfirmationBadgeTest {

    private lateinit var db: IvyRoomDatabase

    /** Paired with an answer this device can render (a transfer it sent). */
    private val shownId = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001")

    /** An answer about a transfer this device has no retained event for. */
    private val unlistableId = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IvyRoomDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun outbox(id: UUID, kind: String) = TransferOutboxEntity(
        correlationId = id,
        kind = kind,
        recordedBy = "owner",
        amount = 25_000.0,
        currency = "IDR",
        occurredOn = "2026-11-01T04:00:00Z",
        fromAccountName = "BCA",
        toAccountName = "Gopay",
        state = "SENT",
        enqueuedAt = 1_761_000_000_000,
    )

    private fun confirmation(id: UUID) = TransferConfirmationEntity(
        correlationId = id,
        decision = "ACCEPTED",
        decidedBy = "so",
        recordedAt = 1_761_000_100_000,
        acknowledged = false,
    )

    /** One listable answer, plus one that the list query can never show. */
    private suspend fun givenOneListableAndOneOrphan() {
        db.writeTransferOutboxDao.enqueueIfAbsent(outbox(shownId, "TRANSFER_EVENT"))
        db.writeTransferConfirmationDao.recordIfAbsent(confirmation(shownId))
        db.writeTransferOutboxDao.enqueueIfAbsent(outbox(unlistableId, "TRANSFER_CONFIRMATION"))
        db.writeTransferConfirmationDao.recordIfAbsent(confirmation(unlistableId))
    }

    private fun acknowledgedFlag(id: UUID): Int = db.openHelper.readableDatabase
        .query(
            "SELECT acknowledged FROM transfer_confirmations WHERE correlationId = ?",
            arrayOf(id.toString()),
        )
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun `the badge counts only the answers the list can show`() = runTest {
        givenOneListableAndOneOrphan()

        db.transferConfirmationDao.findAllWithEvent().size shouldBe 1
        // Counted 2 before the fix: one promised answer that opening the screen
        // never delivered.
        db.transferConfirmationDao.countUnacknowledged() shouldBe 1
    }

    @Test
    fun `acknowledging the rendered rows leaves the unlistable one unread`() = runTest {
        givenOneListableAndOneOrphan()

        db.writeTransferConfirmationDao.acknowledgeMany(listOf(shownId)) shouldBe 1

        acknowledgedFlag(shownId) shouldBe 1
        // Untouched: it is not in the list, so the owner never saw it.
        acknowledgedFlag(unlistableId) shouldBe 0
    }
}

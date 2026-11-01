package com.ivy.data.db.transfer

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.ivy.base.model.TransactionType
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.PendingTransferEntity
import com.ivy.data.db.entity.TransactionEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * [TransferDecisionWriter] against a REAL in-memory database.
 *
 * The JVM test next door (`PendingTransferRepositoryDecideTest`) can only prove
 * that there is one write to be atomic - a mock cannot be rolled back. This one
 * runs the actual statements, so it can show the two outcomes that matter:
 *
 *  - accepting writes BOTH the inbox state and the ledger leg, and a second tap
 *    does not write a second leg;
 *  - an exception inside the transaction leaves the inbox row PENDING, which is
 *    the property the writer's atomicity rests on. Before the writer existed the
 *    same exception left the row ACCEPTED with no ledger row, and `findPending`
 *    would never offer it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransferDecisionWriterTest {

    private lateinit var db: IvyRoomDatabase
    private lateinit var writer: TransferDecisionWriter

    private val correlationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val legId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val accountId = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val senderAccountId = UUID.fromString("33333333-3333-4333-8333-333333333333")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IvyRoomDatabase::class.java,
        ).allowMainThreadQueries().build()

        writer = TransferDecisionWriter(
            db = db,
            pendingDao = db.pendingTransferDao,
            transactionDao = db.writeTransactionDao,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun pendingRow() = PendingTransferEntity(
        correlationId = correlationId,
        recordedByProfileId = "owner",
        recordedByProfileName = "Kiki",
        intoAccountId = accountId,
        fromAccountName = "BCA",
        amount = 25_000.0,
        currency = "IDR",
        occurredOn = Instant.parse("2026-11-01T04:00:00Z"),
        title = "Lunch",
        description = null,
        state = "PENDING",
    )

    private fun leg(id: UUID = legId) = TransactionEntity(
        accountId = accountId,
        type = TransactionType.TRANSFER,
        amount = 25_000.0,
        title = "Lunch",
        dateTime = Instant.parse("2026-11-01T04:00:00Z"),
        counterpartProfileId = "owner",
        id = id,
    )

    private fun legsWritten(): Int = db.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM transactions WHERE type = 'TRANSFER'")
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun `accept writes the decision and the leg together`() = runTest {
        db.pendingTransferDao.insertIfAbsent(pendingRow())

        writer.decideAndWrite(correlationId, "ACCEPTED", leg()) shouldBe true

        db.pendingTransferDao.findState(correlationId) shouldBe "ACCEPTED"
        legsWritten() shouldBe 1
    }

    @Test
    fun `decline writes the decision and no leg`() = runTest {
        db.pendingTransferDao.insertIfAbsent(pendingRow())

        writer.decideAndWrite(correlationId, "DECLINED", null) shouldBe true

        db.pendingTransferDao.findState(correlationId) shouldBe "DECLINED"
        legsWritten() shouldBe 0
    }

    @Test
    fun `a second tap on a decided transfer writes no second leg`() = runTest {
        db.pendingTransferDao.insertIfAbsent(pendingRow())

        writer.decideAndWrite(correlationId, "ACCEPTED", leg()) shouldBe true
        writer.decideAndWrite(correlationId, "ACCEPTED", leg()) shouldBe false

        legsWritten() shouldBe 1
    }

    @Test
    fun `a failure inside the transaction leaves the transfer PENDING and actionable`() = runTest {
        db.pendingTransferDao.insertIfAbsent(pendingRow())

        shouldThrow<IllegalStateException> {
            db.withTransaction {
                db.pendingTransferDao.markDecided(correlationId, "ACCEPTED")
                throw IllegalStateException("the ledger insert died")
            }
        }

        // Still PENDING is the whole point: `findPending` returns it again, so she
        // can retry. The bug this replaces left it ACCEPTED, unreachable forever.
        db.pendingTransferDao.findState(correlationId) shouldBe "PENDING"
        db.pendingTransferDao.findPending().size shouldBe 1
    }

    @Test
    fun `the writer itself is atomic - a failing leg insert rolls the decision back`() = runTest {
        db.pendingTransferDao.insertIfAbsent(pendingRow())

        // The leg insert as the one thing that can fail for real: the ledger DAO is
        // replaced with one that throws, while the inbox DAO stays the real thing so
        // the rollback is observed in the database rather than in a mock's memory.
        val explodingDao = mockk<com.ivy.data.db.dao.write.WriteTransactionDao>()
        coEvery { explodingDao.save(any()) } throws IllegalStateException("ledger insert died")
        val explodingWriter = TransferDecisionWriter(
            db = db,
            pendingDao = db.pendingTransferDao,
            transactionDao = explodingDao,
        )

        shouldThrow<IllegalStateException> {
            explodingWriter.decideAndWrite(correlationId, "ACCEPTED", leg())
        }

        // If `decideAndWrite` ever stops wrapping both statements in withTransaction,
        // this is the assertion that fails: the decision would already be committed.
        db.pendingTransferDao.findState(correlationId) shouldBe "PENDING"
        legsWritten() shouldBe 0
    }
}

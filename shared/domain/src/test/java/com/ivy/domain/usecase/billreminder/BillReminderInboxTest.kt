package com.ivy.domain.usecase.billreminder

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.PendingBillEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * The idempotency and adjustment rules, against a fake that behaves like the
 * database rather than like a map.
 *
 * The fake here is deliberately faithful on the one behaviour that matters:
 * [FakePendingBillStore.insertIfAbsent] refuses a duplicate primary key and
 * reports it, exactly as `INSERT OR IGNORE` does on SQLite. A fake that appended
 * unconditionally would accept the duplicate this test exists to reject, and the
 * suite would pass while the real database grew two bills - which is precisely
 * the failure this project has already shipped once, via a PocketBase collection
 * that declared `unique: true` on a field with no real index behind it.
 *
 * `Migration134to135PendingBillsTest` asserts the same guarantee against a real
 * SQLite engine, because a fake proves the wiring and the engine proves the
 * claim.
 */
class BillReminderInboxTest {

    private val zone = ZoneId.of("Asia/Jakarta")
    private val ruleId = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val accountId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

    private lateinit var store: FakePendingBillStore
    private lateinit var inbox: BillReminderInbox

    @Before
    fun setUp() {
        store = FakePendingBillStore()
        inbox = BillReminderInbox(store, store)
    }

    private suspend fun fabricate(
        dueOn: LocalDate = LocalDate.of(2027, 1, 25),
        amount: Double = 500_000.0,
        id: UUID = ruleId,
    ) = inbox.fabricate(
        ruleId = id,
        dueOn = dueOn,
        amount = amount,
        type = TransactionType.EXPENSE,
        accountId = accountId,
        title = "Internet",
        description = null,
        zone = zone,
    )

    /**
     * A reminder produces exactly one visible, unpaid pending bill.
     *
     * The baseline the other tests correct against.
     */
    @Test
    fun aReminderCreatesOneUnpaidPendingBill() = runTest {
        val result = fabricate()

        (result as BillReminderInbox.Result.Created).bill.state shouldBe
            PendingBillEntity.STATE_PENDING
        inbox.pendingBills().size shouldBe 1
        inbox.pendingBills().first().amount shouldBe 500_000.0
        inbox.pendingBills().first().title shouldBe "Internet"
    }

    /**
     * THE idempotency case: one bill, two deliveries, one row.
     *
     * The second call is what a WorkManager retry, a re-run of the scheduler or a
     * replayed sync looks like from here. It must be refused, and it must report
     * that it was refused so the caller knows not to send a second notification.
     */
    @Test
    fun theSameBillDeliveredTwiceProducesOneRow() = runTest {
        fabricate()
        val replay = fabricate()

        replay shouldBe BillReminderInbox.Result.AlreadyHandled
        inbox.pendingBills().size shouldBe 1
        store.insertAttempts shouldBe 2
        store.refusedInserts shouldBe 1
    }

    /**
     * The occurrence key is derived from the rule and month, so it is stable.
     *
     * This is the property that makes the refusal above possible at all. A random
     * UUID per attempt would be unique every time, and the replay would insert a
     * second bill with a second key.
     */
    @Test
    fun theOccurrenceKeyIsStableForTheSameRuleAndMonth() {
        val key = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25))

        key shouldBe inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25))
        key shouldContain ruleId.toString()
    }

    /** Different months are different bills, so the key must not collide. */
    @Test
    fun differentMonthsGetDifferentKeys() {
        inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25)) shouldNotBe
            inbox.occurrenceKey(ruleId, LocalDate.of(2027, 2, 25))
    }

    /** Different rules in the same month are different bills. */
    @Test
    fun differentRulesGetDifferentKeysInTheSameMonth() {
        val other = UUID.fromString("99999999-8888-7777-6666-555555555555")

        inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25)) shouldNotBe
            inbox.occurrenceKey(other, LocalDate.of(2027, 1, 25))
    }

    /**
     * The key survives UUID hyphen ambiguity.
     *
     * The key is a bracketed string rather than a bare concatenation because a
     * UUID contains hyphens; without unambiguous separators the boundary between
     * the id and the month would be guessable, and two different bills could
     * collide onto one key - losing a bill silently.
     */
    @Test
    fun theKeyDelimitsItsFieldsUnambiguously() {
        val key = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 5))

        // The month is zero-padded, so January is "01" and not "1". Without the
        // padding, a key built for the 5th could be indistinguishable from one
        // built for a differently-padded month.
        key shouldContain "[2027-01]"
    }

    // ---------------------------------------------------------------------
    // Constraint 2: adjust on the fly, never duplicate
    // ---------------------------------------------------------------------

    /**
     * Adjusting the amount edits the row in place.
     *
     * The owner raised the electricity bill. It must be the same row with a new
     * amount, not a second row for the same month.
     */
    @Test
    fun adjustingTheAmountEditsTheRowRatherThanAddingOne() = runTest {
        fabricate(amount = 500_000.0)

        val adjusted = inbox.adjustAmount(
            occurrenceKey = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25)),
            amount = 650_000.0,
        )

        adjusted shouldBe true
        inbox.pendingBills().size shouldBe 1
        inbox.pendingBills().first().amount shouldBe 650_000.0
    }

    /**
     * An adjusted amount survives a replayed reminder.
     *
     * The subtle half of constraint 2. The row exists, so the replay is refused -
     * and because the insert is IGNORE rather than REPLACE, the refusal must not
     * quietly reset the amount back to the rule's figure. An owner who corrected
     * the bill and then saw it snap back would conclude the app had ignored him.
     */
    @Test
    fun aReplayedReminderDoesNotOverwriteAnAdjustedAmount() = runTest {
        fabricate(amount = 500_000.0)
        inbox.adjustAmount(
            occurrenceKey = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25)),
            amount = 650_000.0,
        )

        val replay = fabricate(amount = 500_000.0)

        replay shouldBe BillReminderInbox.Result.AlreadyHandled
        inbox.pendingBills().first().amount shouldBe 650_000.0
    }

    /** A negative amount is rejected rather than written. */
    @Test
    fun aNegativeAmountIsRefused() = runTest {
        fabricate()

        val adjusted = inbox.adjustAmount(
            occurrenceKey = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25)),
            amount = -1.0,
        )

        adjusted shouldBe false
        inbox.pendingBills().first().amount shouldBe 500_000.0
    }

    // ---------------------------------------------------------------------
    // Dismissal and payment leave tombstones
    // ---------------------------------------------------------------------

    /**
     * A dismissed bill leaves the inbox but keeps its row.
     *
     * Retained deliberately: if dismissal deleted the row, the next reminder for
     * the same occurrence would find nothing and re-create it, so a bill the
     * owner threw away would come back on every replay.
     */
    @Test
    fun dismissingRemovesItFromTheInboxButKeepsTheTombstone() = runTest {
        fabricate()
        val key = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25))

        inbox.dismiss(key) shouldBe true

        inbox.pendingBills().size shouldBe 0
        store.rows().size shouldBe 1
        store.rows().first().state shouldBe PendingBillEntity.STATE_DISMISSED
    }

    /** ...and a dismissed bill is not re-created by a later reminder. */
    @Test
    fun aDismissedBillIsNotResurrectedByALaterReminder() = runTest {
        fabricate()
        inbox.dismiss(inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25)))

        fabricate() shouldBe BillReminderInbox.Result.AlreadyHandled

        inbox.pendingBills().size shouldBe 0
        store.rows().size shouldBe 1
    }

    /** Paying returns the bill for conversion, and is not repeatable. */
    @Test
    fun payingSettlesTheBillExactlyOnce() = runTest {
        fabricate()
        val key = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25))

        inbox.markPaid(key)?.state shouldBe PendingBillEntity.STATE_PENDING
        // A replayed "pay" finds nothing pending and returns null, so the caller
        // cannot write a second transaction for one bill.
        inbox.markPaid(key) shouldBe null
        inbox.pendingBills().size shouldBe 0
        store.rows().first().state shouldBe PendingBillEntity.STATE_PAID
    }

    /**
     * Changing the rule's reminder day leaves this month's bill alone.
     *
     * The design decision, pinned. Re-arming the reminder on a new day changes
     * what happens NEXT month; it says nothing about a bill the owner can already
     * see. So the existing row must survive untouched - not deleted (which would
     * erase a bill he may have adjusted the amount on) and not re-created under
     * the new day (which would give him two rows for one bill).
     *
     * Asserted through the only operations a day change actually performs - it
     * never calls into the inbox at all - so this is really pinning that the row
     * is still there and still the owner's, afterwards.
     */
    @Test
    fun changingTheReminderDayLeavesThisMonthsBillUntouched() = runTest {
        fabricate(dueOn = LocalDate.of(2027, 1, 25), amount = 500_000.0)
        val key = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25))
        inbox.adjustAmount(key, 650_000.0)

        // The rule's day changes. Nothing in the inbox is involved: the ViewModel
        // only writes the form field and re-arms the scheduler.
        val nextOccurrenceForNewDay = BillReminderDates.nextOccurrence(
            dayOfMonth = 15,
            now = LocalDateTime.of(2027, 1, 25, 10, 0),
            zone = zone,
        )
        nextOccurrenceForNewDay.atZone(zone).toLocalDate() shouldBe
            LocalDate.of(2027, 2, 15)

        // The pending bill is untouched: same row, same month, same amount.
        inbox.pendingBills().size shouldBe 1
        inbox.pendingBills().first().occurrenceKey shouldBe key
        inbox.pendingBills().first().amount shouldBe 650_000.0
        inbox.pendingBills().first().state shouldBe PendingBillEntity.STATE_PENDING
    }

    /**
     * Next month's bill under the new day is a genuinely different bill.
     *
     * The other half: the day change has to actually take effect somewhere, or the
     * rule would be inert. It applies to the following occurrence, which gets its
     * own occurrence key and therefore its own row - not a second row for January.
     */
    @Test
    fun theNewDayAppliesToTheFollowingMonthAsItsOwnBill() = runTest {
        // January's bill, under the original day.
        fabricate(dueOn = LocalDate.of(2027, 1, 25))
        // February's, now that the rule says the 15th.
        val february = fabricate(dueOn = LocalDate.of(2027, 2, 15))

        (february is BillReminderInbox.Result.Created) shouldBe true
        // Two bills, two months, two keys - not a duplicate of January.
        store.rows().size shouldBe 2
        inbox.pendingBills().map { it.occurrenceKey }.toSet().size shouldBe 2
    }

    /** A paid bill is not editable afterwards - its amount is a historical fact. */
    @Test
    fun aPaidBillAmountCannotBeEditedAfterwards() = runTest {
        fabricate()
        val key = inbox.occurrenceKey(ruleId, LocalDate.of(2027, 1, 25))
        inbox.markPaid(key)

        inbox.adjustAmount(key, 999_000.0) shouldBe false

        store.rows().first().amount shouldBe 500_000.0
    }
}
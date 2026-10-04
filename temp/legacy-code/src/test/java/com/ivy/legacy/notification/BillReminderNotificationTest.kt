package com.ivy.legacy.notification

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.PendingBillEntity
import com.ivy.domain.usecase.billreminder.BillReminderInbox
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * The decision the reminder worker makes, tested without a device.
 *
 * ## What is actually under test
 *
 * The worker cannot be constructed on the JVM - `CoroutineWorker` needs a real
 * Android `Context` - so this tests the decision it delegates to, which is the
 * part that carries constraint 3 and is easy to get backwards.
 *
 * Constraint 3: **the notification must fire independently of whether the pending
 * row was ever opened.** Concretely, two things must not be conflated:
 *
 *  - "a pending bill exists" is NOT evidence "the owner was told about it".
 *  - "the owner acted on it" is NOT a precondition for telling them.
 *
 * The bug this prevents is a reminder that goes quiet once the row exists - e.g.
 * a worker that skips notifying because it found a pending bill already there, or
 * one that marks the row "notified" only after the notification succeeds and then
 * treats a suppressed post as a delivered one. Either way the owner stops being
 * told about a bill that is genuinely due, and nothing looks broken.
 *
 * The rule is therefore: **notify on first fabrication, never on a replay.** Not
 * "notify while the row is unopened" and not "notify until acknowledged".
 */
class BillReminderNotificationTest {

    private val zone = ZoneId.of("Asia/Jakarta")
    private val ruleId = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val accountId = UUID.randomUUID()
    private val dueOn = LocalDate.of(2027, 1, 25)

    private fun inbox(store: FakePendingBillStore) = BillReminderInbox(store, store)

    private suspend fun fabricate(
        inbox: BillReminderInbox,
        amount: Double = 500_000.0,
    ) = inbox.fabricate(
        ruleId = ruleId,
        dueOn = dueOn,
        amount = amount,
        type = TransactionType.EXPENSE,
        accountId = accountId,
        title = "Internet",
        description = null,
        zone = zone,
    )

    /**
     * Firing the reminder notifies, and does so on the strength of having
     * fabricated - not on anything the owner did.
     *
     * `Created` is the worker's cue to post. The owner has not opened, read,
     * acknowledged or paid anything at this point, and the notification is posted
     * anyway. That is the whole content of constraint 3.
     */
    @Test
    fun theFirstFiringAsksForANotification() = runTest {
        val store = FakePendingBillStore()

        val outcome = fabricate(inbox(store))

        // The worker branches on exactly this: Created -> notify.
        (outcome is BillReminderInbox.Result.Created) shouldBe true
        store.insertAttempts shouldBe 1
        store.refusedInserts shouldBe 0
    }

    /**
     * A retry after a successful notification must NOT notify again.
     *
     * The other half of the same rule. Constraint 3 is about not confusing
     * delivery with action; this is about not confusing *idempotency* with
     * *never telling the owner*. A WorkManager retry that re-notified would leave
     * the owner with duplicate notifications every time the system retried, which
     * is its own way of training him to ignore the feature.
     */
    @Test
    fun aRetryAfterDeliveryDoesNotNotifyAgain() = runTest {
        val store = FakePendingBillStore()
        val inbox = inbox(store)

        val first = fabricate(inbox)
        val retry = fabricate(inbox)

        (first is BillReminderInbox.Result.Created) shouldBe true
        // AlreadyHandled is the worker's cue to stay silent.
        retry shouldBe BillReminderInbox.Result.AlreadyHandled
        store.insertAttempts shouldBe 2
        store.refusedInserts shouldBe 1
    }

    /**
     * The notification is owed even though nothing about the row is "read".
     *
     * There is deliberately no `notifiedAt`, `seen` or `opened` field anywhere in
     * `PendingBillEntity`. This asserts the absence at the point it matters: the
     * first fabrication produces a row whose every field is a fact about the bill,
     * and nothing in it records that the owner has been told - because delivery is
     * not recorded as a property of the row.
     *
     * If a future change adds such a flag, this test is the one that should make
     * the author justify it: it is the field that would let "delivered" and "acted
     * on" start shadowing each other.
     */
    @Test
    fun thePendingRowCarriesNoRecordOfWhetherItWasSeen() = runTest {
        val store = FakePendingBillStore()
        val outcome = fabricate(inbox(store)) as BillReminderInbox.Result.Created

        // Every property of the row is a fact about the bill itself.
        outcome.bill.state shouldBe PendingBillEntity.STATE_PENDING
        outcome.bill.amount shouldBe 500_000.0
        outcome.bill.title shouldBe "Internet"
        outcome.bill.occurrenceKey shouldBe inbox(store).occurrenceKey(ruleId, dueOn)

        // And there is no notification-related state on the entity at all.
        PendingBillEntity::class.java.declaredFields.map { it.name }.none {
            it.contains("notif", ignoreCase = true) ||
                it.contains("seen", ignoreCase = true) ||
                it.contains("opened", ignoreCase = true) ||
                it.contains("read", ignoreCase = true)
        } shouldBe true
    }

    /**
     * Paying the bill does not retroactively suppress this month's reminder.
     *
     * The pathological version of the confusion: the worker checks whether the
     * bill is already settled and, finding it so, stays quiet - so the one month
     * the owner actually acted gets no notification while the months he ignored
     * do. Delivery must depend on whether *this firing* was the first, and on
     * nothing about the row's fate afterwards.
     */
    @Test
    fun payingTheBillDoesNotSuppressTheReminderForThatMonth() = runTest {
        val store = FakePendingBillStore()
        val inbox = inbox(store)
        val key = inbox.occurrenceKey(ruleId, dueOn)

        // The first firing notifies him and creates the row.
        (fabricate(inbox) is BillReminderInbox.Result.Created) shouldBe true

        // He opens the pending bill and marks it paid.
        inbox.markPaid(key)

        // The row is settled, so the inbox is empty...
        inbox.pendingBills().size shouldBe 0
        // ...and a replay of the same firing is still refused rather than
        // re-notifying, because the occurrence was already handled.
        fabricate(inbox) shouldBe BillReminderInbox.Result.AlreadyHandled
        store.rows().size shouldBe 1
    }

    /**
     * An adjustment does not suppress the reminder either.
     *
     * The owner corrected the amount before acting on it. That is still an unpaid
     * bill, still owed a notification, and must not be mistaken for "handled".
     */
    @Test
    fun adjustingTheAmountDoesNotCountAsHandling() = runTest {
        val store = FakePendingBillStore()
        val inbox = inbox(store)
        val key = inbox.occurrenceKey(ruleId, dueOn)

        // The reminder fired first; there is a row to adjust.
        (fabricate(inbox) is BillReminderInbox.Result.Created) shouldBe true

        inbox.adjustAmount(key, 650_000.0) shouldBe true

        // Still pending, still unnotified-as-far-as-this-firing-is-concerned.
        inbox.pendingBills().size shouldBe 1
        inbox.pendingBills().first().state shouldBe PendingBillEntity.STATE_PENDING
    }
}
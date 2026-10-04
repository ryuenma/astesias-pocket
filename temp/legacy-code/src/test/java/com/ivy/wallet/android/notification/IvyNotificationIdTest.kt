package com.ivy.wallet.android.notification

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import java.util.UUID

/**
 * Why notification ids are allocated rather than typed in.
 *
 * A notification id is a slot, not a label: `notify(id, ...)` *replaces* whatever
 * is already in that slot and `cancel(id)` empties it. Two features sharing a
 * literal therefore do not both appear - the second silently overwrites the first,
 * with no error and no visible symptom beyond a notification that never shows up.
 *
 * That is not hypothetical here: `TransactionReminderWorker` used to hardcode
 * `showNotification(notification, 1)`. The moment a second notification type was
 * added, one of the two would have gone missing depending on which ran last.
 *
 * These are pure JVM tests - no Android, no device - because the allocation rule
 * is arithmetic and the failure mode is arithmetic.
 */
class IvyNotificationIdTest {

    /**
     * The daily reminder keeps the single id it always had.
     *
     * Pinned because it is a persisted side effect: changing this id silently
     * leaves the previous day's notification stranded in the shade, unable to be
     * replaced or cancelled by the new id.
     */
    @Test
    fun theDailyReminderHasOneStableId() {
        IvyNotificationId.TRANSACTION_REMINDER shouldBe
            IvyNotificationId.TRANSACTION_REMINDER
        IvyNotificationId.TRANSACTION_REMINDER shouldBe 1_000
    }

    /** A bill reminder can never land in the daily reminder's slot. */
    @Test
    fun billRemindersNeverCollideWithTheDailyReminder() {
        val ruleId = UUID.randomUUID()

        IvyNotificationId.billReminder(ruleId) shouldNotBe
            IvyNotificationId.TRANSACTION_REMINDER
    }

    /**
     * The same rule always gets the same id.
     *
     * This is what makes a bill's notification replace itself next month instead
     * of stacking a fresh copy every time the bill comes round.
     */
    @Test
    fun theSameRuleAlwaysGetsTheSameId() {
        val ruleId = UUID.fromString("11111111-2222-3333-4444-555555555555")

        IvyNotificationId.billReminder(ruleId) shouldBe IvyNotificationId.billReminder(ruleId)
    }

    /**
     * Different rules get different ids.
     *
     * Two bills must not overwrite one another's notification.
     *
     * Sampled at 50 because the derivation is a folded hash and cannot be proved
     * injective. This is a meaningful assertion only because the block is large:
     * in the 1_000-slot version of this file the identical test failed, since 50
     * items in 1_000 slots collide 71% of the time. At the current block size the
     * collision probability is ~6e-7, so a failure here would mean the derivation
     * had actually changed.
     */
    @Test
    fun differentRulesGetDifferentIds() {
        val ids = (1..50).map { IvyNotificationId.billReminder(UUID.randomUUID()) }

        ids.toSet().size shouldBe ids.size
    }

    /**
     * Ids stay inside the bill block.
     *
     * A derived id that escaped its block could overwrite an unrelated feature's
     * notification - the exact class of bug allocation exists to prevent.
     */
    @Test
    fun idsStayInsideTheBillBlock() {
        (1..200).forEach {
            val id = IvyNotificationId.billReminder(UUID.randomUUID())
            (id >= IvyNotificationId.BILL_REMINDER_BASE) shouldBe true
            (id > 0) shouldBe true
        }
    }

    /**
     * The hash is folded, so a negative `hashCode` does not produce a negative id.
     *
     * `UUID.hashCode` can be negative, and `BILL_REMINDER_BASE + negative` would
     * be a negative id. `NotificationManager` accepts it, but it would fall
     * outside the documented block and could collide with the daily reminder's
     * range - so the fold is a correctness requirement, not tidiness.
     */
    @Test
    fun negativeHashCodesStillProduceValidPositiveIds() {
        // Chosen so at least one of these hashes negative; the assertion is on the
        // id being in range regardless.
        listOf(
            "00000000-0000-0000-0000-000000000001",
            "ffffffff-ffff-ffff-ffff-ffffffffffff",
            "80000000-0000-0000-0000-000000000000",
        ).forEach { raw ->
            val id = IvyNotificationId.billReminder(UUID.fromString(raw))
            (id >= IvyNotificationId.BILL_REMINDER_BASE) shouldBe true
            (id > 0) shouldBe true
        }
    }

    /**
     * The string overload matches the UUID overload.
     *
     * The pending-bill row carries the rule id as text, so the worker looks it up
     * by string. If the two paths disagreed, a notification would be posted under
     * an id it could never later replace or cancel.
     */
    @Test
    fun theStringAndUuidOverloadsAgree() {
        val ruleId = UUID.randomUUID()

        IvyNotificationId.billReminder(ruleId.toString()) shouldBe
            IvyNotificationId.billReminder(ruleId)
    }

    /**
     * A malformed rule id does not throw.
     *
     * This runs inside a worker that is trying to tell the owner something; a parse
     * exception there would lose the notification entirely, which is a worse
     * failure than sharing a slot with one other bill.
     */
    @Test
    fun anUnparseableRuleIdFallsBackInsteadOfThrowing() {
        IvyNotificationId.billReminder("not-a-uuid") shouldBe
            IvyNotificationId.BILL_REMINDER_BASE
    }
}
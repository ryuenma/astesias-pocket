package com.ivy.wallet.android.notification

import java.util.UUID

/**
 * Notification ids, allocated rather than guessed.
 *
 * A notification id is not cosmetic: `NotificationManager.notify(id, ...)`
 * *replaces* any notification already posted under that id, and `cancel(id)`
 * removes it. Two features sharing a literal therefore do not both appear - the
 * second silently overwrites the first. That is why this exists rather than
 * another `showNotification(notification, 1)`.
 *
 * The layout is one contiguous block per feature, so a new feature takes the next
 * free block and cannot collide with an existing one by accident:
 *
 * ```
 *   1_000 .. 1_999        transaction reminders   (daily, one id)
 *   2_000 .. MAX_INT     bill reminders          (one id per rule)
 * ```
 *
 * Within the bill block the id is derived from the rule id, so each bill has a
 * stable notification of its own: re-notifying about the same rule *updates* that
 * notification instead of stacking a second copy, and two different bills do not
 * overwrite one another.
 *
 * Derivation is `hashCode` folded into the block, so a collision between two rule
 * ids is possible in principle. The block is sized so that this is negligible
 * rather than merely unlikely (see `BILL_REMINDER_SLOTS`), and a residual
 * collision would be cosmetic anyway - two bills would share one notification slot
 * and the newer would replace the older, while their pending rows stayed separate
 * and correct. A database sequence would remove the possibility entirely, but a
 * stable id that requires no state is what lets the notification be re-armed after
 * a reboot without consulting anything.
 */
object IvyNotificationId {

    /** First id of the daily transaction-reminder block. */
    const val TRANSACTION_REMINDER_BASE = 1_000

    /** First id of the per-rule bill-reminder block. */
    const val BILL_REMINDER_BASE = 2_000

    /**
     * Size of the bill block, and therefore the modulus.
     *
     * As large as a positive notification id can be, which is the point. An
     * earlier version of this file used 1_000 slots and asserted that 50 random
     * rules got 50 distinct ids; that assertion failed, because 50 items in 1_000
     * slots collide 71% of the time by the birthday bound - and even 10 bills
     * collide 4.4% of the time. A collision is not catastrophic (the newer
     * notification replaces the older; the pending rows are separate and
     * unaffected), but "4% of installs have two bills sharing a slot" is not a
     * property worth shipping when the fix is free.
     *
     * At roughly 2.1 billion slots the same collision is ~6e-7 for 50 bills, so
     * the derivation is effectively injective in practice while still needing no
     * state: no database sequence, no counter to keep, and therefore nothing to
     * consult when re-arming after a reboot.
     */
    private val BILL_REMINDER_SLOTS: Int = Int.MAX_VALUE - BILL_REMINDER_BASE

    /**
     * The daily transaction reminder's id.
     *
     * A single stable id, not one per day: a reminder is a prompt about *now*, so
     * today's should replace yesterday's rather than sit above it in the shade.
     */
    const val TRANSACTION_REMINDER: Int = TRANSACTION_REMINDER_BASE

    /**
     * The notification id for [ruleId]'s bill reminder.
     *
     * Stable for the life of the rule, which is what makes the notification
     * replaceable rather than accumulative across months.
     */
    fun billReminder(ruleId: UUID): Int =
        BILL_REMINDER_BASE + Math.floorMod(ruleId.hashCode(), BILL_REMINDER_SLOTS)

    /**
     * The notification id for a bill reminder identified by its rule id string.
     *
     * The string overload exists because the pending-bill row carries the rule id
     * as text; parsing a malformed value must not throw in a notification path, so
     * an unparseable id falls back to a fixed slot rather than crashing the worker
     * that was trying to tell the owner something.
     */
    fun billReminder(ruleId: String): Int {
        val parsed = runCatching { UUID.fromString(ruleId) }.getOrNull()
        return if (parsed != null) {
            billReminder(parsed)
        } else {
            BILL_REMINDER_BASE
        }
    }
}
package com.ivy.domain.usecase.billreminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a monthly bill reminder fires, and what its occurrence key is.
 *
 * Pure date arithmetic with no Android, no database and no WorkManager, so the
 * parts that are easy to get wrong - month ends, year ends, and the identity of
 * "the reminder for rule R in month M" - are testable on the JVM.
 *
 * ## Why the existing 24-hour periodic work cannot do this job
 *
 * `TransactionReminderLogic` uses
 * `PeriodicWorkRequestBuilder<TransactionReminderWorker>(24, TimeUnit.HOURS)`.
 * Three separate reasons make that incapable of hitting a chosen day of the
 * month:
 *
 *  1. **Periodicity cannot express a calendar position.** "Every 24 hours" and
 *     "the 25th of each month" are different statements. Months are 28, 29, 30
 *     or 31 days, so a 24-hour period walks across the month: work that started
 *     on the 25th lands on the 26th within two months. There is no interval
 *     length that is simultaneously correct in February and in March.
 *  2. **It is inexact under Doze.** WorkManager's periodic work is a *request*,
 *     not an alarm. The system batches and defers it to save power, so the actual
 *     run time drifts by minutes or hours. A bill reminder that arrives after
 *     midnight has arrived on the wrong day, which for a due-date reminder is the
 *     difference between useful and noise.
 *  3. **It cannot re-anchor itself.** The existing worker returns
 *     `Result.success()` and never re-enqueues anything, so even a correct
 *     implementation could not compute "the next occurrence" - there is nowhere
 *     in the codebase that does that today. A month-aware reminder has to
 *     recompute its own next fire time from the calendar after every run.
 *
 * So this type computes an absolute [Instant] for the next occurrence, and the
 * scheduler enqueues a *one-shot* request with an initial delay to reach it,
 * re-arming the following month from inside the worker.
 */
object BillReminderDates {

    /**
     * The time of day a reminder fires, local to the owner.
     *
     * Morning rather than the daily transaction reminder's 8 PM: a bill due
     * today is something he may still be able to act on before the shop closes,
     * and the two reminders are independent so there is no reason to make them
     * collide.
     */
    val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * The day-of-month bounds a rule may be set to.
     *
     * 31 is included because it is a real day in seven months of the year, and
     * clamping (below) gives it a meaning in the four where it is not.
     */
    const val MIN_DAY_OF_MONTH = 1
    const val MAX_DAY_OF_MONTH = 31

    /**
     * Whether [dayOfMonth] is a day a rule may be reminded on.
     *
     * Checked before scheduling rather than clamped on the way in, so a rule
     * cannot be saved with a nonsense day that then silently behaves like a
     * different one.
     */
    fun isValidDayOfMonth(dayOfMonth: Int?): Boolean =
        dayOfMonth != null && dayOfMonth in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH

    /**
     * The day a bill set to [dayOfMonth] falls on in [month], clamped to that
     * month's length.
     *
     * ## The month-end decision, and why it is clamp
     *
     * A bill set to the 31st has no exact equivalent in February. There are
     * three defensible rules and this picks one:
     *
     *  - **Clamp** (chosen): the reminder fires on the last day of the shorter
     *    month. The 31st becomes the 28th in a common February and the 29th in
     *    a leap one.
     *  - Skip: no reminder at all in months that lack the day.
     *  - Drift: `IntervalTypeExt.incrementDate` already does this - it uses
     *    `plusMonths`, so Jan 31 becomes Feb 28 and then **Mar 28**, and never
     *    returns to the 31st. The rule silently rewrites itself to a different
     *    day after one short month.
     *
     * Clamp is chosen over skip because a skipped month is a silently dropped
     * bill: the owner set a monthly reminder and one month it simply does not
     * exist, with nothing on screen to say so. Clamp over drift because drift is
     * the one behaviour already in this codebase that gets it wrong, and because
     * it is irreversible - a rule that drifted to the 28th stays there for the
     * life of the install, so the owner is reminded on the wrong day forever and
     * has no way to see that it happened.
     *
     * Clamp is also the reading that matches what a person means by "the 31st" for
     * a bill: the last day of the month it is billed on. It fires every month, it
     * is never more than three days early, and it returns to the configured day
     * as soon as a long month comes round.
     *
     * The clamp is computed from [dayOfMonth] each time rather than by
     * incrementing a previous date, which is precisely what makes it return to
     * the 31st in March instead of staying on the 28th.
     */
    fun clampToMonth(month: java.time.YearMonth, dayOfMonth: Int): LocalDate =
        LocalDate.of(
            month.year,
            month.month,
            dayOfMonth.coerceAtMost(month.lengthOfMonth())
        )

    /**
     * The next occurrence of a [dayOfMonth] reminder strictly after [now].
     *
     * "Strictly after" is deliberate. It makes this function safe to call from
     * the worker that is *currently* running for an occurrence: re-arming from
     * inside the worker computes the following month rather than handing back the
     * occurrence just handled, which would loop forever on the same date.
     *
     * Local time throughout. The comparison is done on the local calendar day
     * because that is the frame the owner means "the 25th" in; converting to UTC
     * before comparing would move a 9 AM reminder across a day boundary for
     * anyone far enough from Greenwich.
     */
    fun nextOccurrence(
        dayOfMonth: Int,
        now: LocalDateTime,
        zone: ZoneId,
        reminderTime: LocalTime = REMINDER_TIME,
    ): Instant {
        val today = now.toLocalDate()
        val thisMonth = java.time.YearMonth.from(today)

        // Today is still a candidate if the day's reminder time has not passed.
        // Comparing local times rather than instants keeps this correct across a
        // DST boundary, where the same wall-clock time is a different instant.
        val todayOccurrence = clampToMonth(thisMonth, dayOfMonth)
            .atTime(reminderTime)
        if (todayOccurrence.isAfter(now)) {
            return todayOccurrence.atZone(zone).toInstant()
        }

        val nextMonthDate = clampToMonth(thisMonth.plusMonths(1), dayOfMonth)
        return nextMonthDate.atTime(reminderTime).atZone(zone).toInstant()
    }

    /**
     * The local midnight at which a bill due on [dueOn] is dated.
     *
     * Round-tripping an epoch-millis instant through the zone is what keeps a bill
     * due on the 25th from sliding to the 24th or the 26th for an owner whose
     * offset is not a whole number of hours.
     */
    fun dueOnInstant(localDate: LocalDate, zone: ZoneId): Instant =
        localDate.atStartOfDay(zone).toInstant()
}

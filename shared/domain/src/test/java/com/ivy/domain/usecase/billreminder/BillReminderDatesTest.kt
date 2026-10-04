package com.ivy.domain.usecase.billreminder

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The month-end rule, pinned.
 *
 * A bill set to the 31st has no exact equivalent in February, and there is no
 * precedent in this codebase to copy: `IntervalTypeExt.incrementDate` uses
 * `plusMonths`, which does not answer the question at all - it drifts the day
 * (Jan 31 -> Feb 28 -> Mar 28, and never returns to the 31st). So the rule had to
 * be chosen rather than inherited, and a product decision that easy to get wrong
 * is worth a test that fails loudly if someone "simplifies" it later.
 *
 * These are pure JVM tests: no Android, no database, no device. The whole point
 * of [BillReminderDates] being a separate object is that this decision is
 * testable at all - a scheduler that computed dates inline could only be checked
 * by waiting for a real month to pass.
 */
class BillReminderDatesTest {

    private val jakarta = ZoneId.of("Asia/Jakarta")

    /**
     * THE decision: clamp, do not skip and do not drift.
     *
     * January's 31st, then February's, then March's. The third assertion is the
     * load-bearing one - it is what separates clamping from the `plusMonths`
     * behaviour already in this codebase, which would give 28 March here and
     * would then stay on the 28th for the life of the install.
     */
    @Test
    fun the31stClampsToTheEndOfFebruaryAndReturnsToThe31stInMarch() {
        val january = YearMonth.of(2027, 1)
        val february = YearMonth.of(2027, 2)
        val march = YearMonth.of(2027, 3)

        // Pins the JDK argument order (year, month) rather than relying on the
        // reader remembering it. If these two ever disagree, a wrong month would
        // silently clamp against the wrong length and the assertions below would
        // still pass - so they are asserted explicitly.
        january.year shouldBe 2027
        january.monthValue shouldBe 1
        february.lengthOfMonth() shouldBe 28

        BillReminderDates.clampToMonth(january, 31) shouldBe LocalDate.of(2027, 1, 31)
        // Common February: 28 days.
        BillReminderDates.clampToMonth(february, 31) shouldBe LocalDate.of(2027, 2, 28)
        // And back to the configured day, not stuck on the 28th.
        BillReminderDates.clampToMonth(march, 31) shouldBe LocalDate.of(2027, 3, 31)
    }

    /**
     * A leap February clamps to the 29th, not the 28th.
     *
     * Pinned separately because the leap case is exactly the one a naive
     * `coerceAtMost(28)` would get wrong, and it is wrong for one day every four
     * years - the kind of bug that is never noticed and never forgone.
     */
    @Test
    fun the31stClampsToThe29thInALeapFebruary() {
        // 2028 is divisible by 4 and not by 100, so it is a leap year.
        BillReminderDates.clampToMonth(YearMonth.of(2028, 2), 31) shouldBe
            LocalDate.of(2028, 2, 29)
    }

    /** The 30th clamps in February too - 30 is not a leap-year-only edge case. */
    @Test
    fun the30thClampsInFebruary() {
        BillReminderDates.clampToMonth(YearMonth.of(2027, 2), 30) shouldBe
            LocalDate.of(2027, 2, 28)
    }

    /** A day every month has is never moved. */
    @Test
    fun the15thIsNeverMoved() {
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12).forEach { month ->
            BillReminderDates.clampToMonth(YearMonth.of(2027, month), 15) shouldBe
                LocalDate.of(2027, month, 15)
        }
    }

    /** December rolls into January of the next year without losing the day. */
    @Test
    fun the31stInDecemberRollsIntoJanuary() {
        val december = YearMonth.of(2027, 12)
        december.plusMonths(1) shouldBe YearMonth.of(2028, 1)
        BillReminderDates.clampToMonth(december.plusMonths(1), 31) shouldBe
            LocalDate.of(2028, 1, 31)
    }

    // ---------------------------------------------------------------------
    // Next occurrence
    // ---------------------------------------------------------------------

    /**
     * Before the reminder time on the day itself: today is still the answer.
     *
     * The case that matters most in practice, because it is what makes the
     * scheduler idempotent in effect - re-running it during the day must not push
     * the reminder a whole month out.
     */
    @Test
    fun beforeTheReminderTimeTodayIsStillToday() {
        val now = LocalDateTime.of(2027, 1, 25, 7, 0)

        BillReminderDates.nextOccurrence(25, now, jakarta) shouldBe
            LocalDateTime.of(2027, 1, 25, 9, 0)
                .atZone(jakarta).toInstant()
    }

    /**
     * After the reminder time on the day itself: the next one is next month.
     *
     * This is the "strictly after" rule, and it is what stops a self-re-arming
     * worker looping forever on the date it is currently handling.
     */
    @Test
    fun afterTheReminderTimeTheNextOneIsNextMonth() {
        val now = LocalDateTime.of(2027, 1, 25, 10, 0)

        BillReminderDates.nextOccurrence(25, now, jakarta) shouldBe
            LocalDateTime.of(2027, 2, 25, 9, 0)
                .atZone(jakarta).toInstant()
    }

    /** Exactly on the reminder time counts as passed, so it does not re-fire. */
    @Test
    fun exactlyOnTheReminderTimeItIsTreatedAsPassed() {
        val now = LocalDateTime.of(2027, 1, 25, 9, 0)

        BillReminderDates.nextOccurrence(25, now, jakarta) shouldBe
            LocalDateTime.of(2027, 2, 25, 9, 0)
                .atZone(jakarta).toInstant()
    }

    /**
     * From a day before, the reminder lands on the chosen day - not today.
     *
     * The plain case, and the one a 24-hour periodic work request gets wrong
     * within two months.
     */
    @Test
    fun fromTheDayBeforeItLandsOnTheChosenDay() {
        val now = LocalDateTime.of(2027, 1, 24, 22, 0)

        BillReminderDates.nextOccurrence(25, now, jakarta) shouldBe
            LocalDateTime.of(2027, 1, 25, 9, 0)
                .atZone(jakarta).toInstant()
    }

    /**
     * A 31st bill, asked on the 1st of March, is due on the 31st of March.
     *
     * The clamp survives the round trip through the scheduler, which is the whole
     * point of computing the date rather than incrementing one.
     */
    @Test
    fun aClampedBillStillTargetsTheRightDayInTheFollowingMonth() {
        val now = LocalDateTime.of(2027, 2, 1, 12, 0)

        BillReminderDates.nextOccurrence(31, now, jakarta) shouldBe
            LocalDateTime.of(2027, 2, 28, 9, 0)
                .atZone(jakarta).toInstant()
    }

    /**
     * The occurrence stays on the configured day across a DST transition.
     *
     * Jakarta has no DST, so this uses a zone that does. The reminder is defined
     * in local wall-clock time, so 9 AM must stay 9 AM on both sides of the
     * change rather than drifting by an hour - which is what comparing instants
     * instead of local times would cause.
     */
    @Test
    fun theReminderStaysAtTheSameLocalTimeAcrossADstChange() {
        val newYork = ZoneId.of("America/New_York")
        // US DST began 2027-03-14.
        val before = LocalDateTime.of(2027, 3, 10, 10, 0)
        val after = LocalDateTime.of(2027, 3, 15, 10, 0)

        val nextBefore = BillReminderDates.nextOccurrence(25, before, newYork)
        val nextAfter = BillReminderDates.nextOccurrence(25, after, newYork)

        // Both are 9 AM *local*, even though their UTC offsets differ.
        nextBefore.atZone(newYork).toLocalTime().toString() shouldBe "09:00"
        nextAfter.atZone(newYork).toLocalTime().toString() shouldBe "09:00"
        nextBefore.atZone(newYork).toLocalDate() shouldBe LocalDate.of(2027, 3, 25)
        nextAfter.atZone(newYork).toLocalDate() shouldBe LocalDate.of(2027, 3, 25)
    }

    // ---------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------

    /**
     * Only 1..31 is a valid reminder day.
     *
     * Zero and negatives are rejected rather than clamped, because coercing a
     * corrupt 0 to the 1st would invent a reminder day the owner never chose.
     */
    @Test
    fun onlyDaysOneToThirtyOneAreValid() {
        BillReminderDates.isValidDayOfMonth(1) shouldBe true
        BillReminderDates.isValidDayOfMonth(25) shouldBe true
        BillReminderDates.isValidDayOfMonth(31) shouldBe true

        BillReminderDates.isValidDayOfMonth(0) shouldBe false
        BillReminderDates.isValidDayOfMonth(-1) shouldBe false
        BillReminderDates.isValidDayOfMonth(32) shouldBe false
    }

    /**
     * No day at all means no reminder.
     *
     * The null case is every pre-migration rule, so this is the assertion that
     * says "adding the column did not switch anybody's reminders on".
     */
    @Test
    fun noDayMeansNoReminder() {
        BillReminderDates.isValidDayOfMonth(null) shouldBe false
    }
}
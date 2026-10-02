package com.ivy.transaction

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Why the default time-of-day is NOON and not midnight.
 *
 * Money is stored as a UTC instant. A date the user picks is a local calendar
 * day, so converting local -> UTC can move the instant onto a DIFFERENT UTC day.
 * When that happens the transaction is filed under the wrong date, which is the
 * off-by-one this fork inherits from upstream.
 *
 * Local midnight is the worst possible default: it sits exactly on the day
 * boundary, so every eastward offset shifts it backwards. Indonesia is UTC+7.
 * Noon has the whole day either side of it and survives every real offset.
 */
class DefaultTimeOfDayTest {

    private val pickedDate: LocalDate = LocalDate.of(2026, 10, 2)

    private fun utcDay(local: LocalDateTime, offsetHours: Int): LocalDate =
        local.toInstant(ZoneOffset.ofTotalSeconds(offsetHours * 3600))
            .atZone(ZoneOffset.UTC)
            .toLocalDate()

    @Test
    fun `local midnight lands on the wrong UTC day for every eastward offset`() {
        val broken = (-11..12).filter { offset ->
            val localMidnight = pickedDate.atStartOfDay()
            utcDay(localMidnight, offset) != pickedDate
        }

        // If this ever stops being true the reasoning above needs revisiting, not
        // the test.
        broken shouldBe (1..12).toList()
    }

    @Test
    fun `noon lands on the picked date at every real-world offset`() {
        val noon = pickedDate.atTime(12, 0)

        val wrong = (-11..12).filter { offset -> utcDay(noon, offset) != pickedDate }

        wrong shouldBe emptyList()
    }

    @Test
    fun `Indonesia is inside the range that midnight breaks`() {
        val indonesiaOffset = 7

        val localMidnight = pickedDate.atStartOfDay()

        // Parenthesised: Kotlin parses `a != b shouldBe true` as `a != (b shouldBe true)`,
        // because the infix call binds tighter than the comparison.
        (utcDay(localMidnight, indonesiaOffset) != pickedDate) shouldBe true
        utcDay(pickedDate.atTime(12, 0), indonesiaOffset) shouldBe pickedDate
    }
}
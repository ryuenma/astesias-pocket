package com.ivy.transaction

import com.ivy.base.time.TimeConverter
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A new transaction the user never gave a time to was stamped with the instant of
 * saving. That is a *moment*, and the date it belongs to depends on where you are:
 * a save at 02:00 in Jakarta is 19:00 the previous day in UTC, so the transaction
 * was filed under yesterday and its balance movement showed up a day early on any
 * report read in UTC.
 *
 * The date picker already solved this by putting the time at local noon; the create
 * path never asked. These tests pin the create path to the same rule.
 */
class DefaultTransactionDateTimeTest {

    /** A [TimeConverter] pinned to one zone, so the assertions mean something. */
    private class FixedZoneConverter(private val zone: ZoneId) : TimeConverter {
        override fun Instant.toLocalDateTime(): LocalDateTime = atZone(zone).toLocalDateTime()
        override fun Instant.toLocalDate(): LocalDate = atZone(zone).toLocalDate()
        override fun Instant.toLocalTime(): LocalTime = atZone(zone).toLocalTime()
        override fun LocalDateTime.toUTC(): Instant = atZone(zone).toInstant()
    }

    @Test
    fun `a 2am save in Jakarta is filed today, not yesterday`() {
        // 2026-11-01T19:00Z is 2026-11-02 02:00 in Jakarta.
        val now = Instant.parse("2026-11-01T19:00:00Z")

        val stamped = defaultTransactionDateTime(now, FixedZoneConverter(ZoneId.of("Asia/Jakarta")))

        // Noon on the 2nd in Jakarta is 05:00 UTC on the 2nd.
        // The instant of saving (2026-11-01T19:00Z) would have filed it under the 1st.
        stamped shouldBe Instant.parse("2026-11-02T05:00:00Z")
    }

    @Test
    fun `a late-evening save west of UTC is also filed today`() {
        // 2026-11-02T03:00Z is 2026-11-01 22:00 in New York: saving late at night
        // must not push the transaction into tomorrow.
        val now = Instant.parse("2026-11-02T03:00:00Z")

        val stamped = defaultTransactionDateTime(
            now,
            FixedZoneConverter(ZoneId.of("America/New_York")),
        )

        // Noon on the 1st in New York (UTC-5 in November) is 17:00 UTC.
        stamped shouldBe Instant.parse("2026-11-01T17:00:00Z")
    }

    @Test
    fun `a save in UTC is stamped at noon the same day`() {
        val now = Instant.parse("2026-11-01T23:30:00Z")

        val stamped = defaultTransactionDateTime(now, FixedZoneConverter(ZoneId.of("UTC")))

        stamped shouldBe Instant.parse("2026-11-01T12:00:00Z")
    }
}

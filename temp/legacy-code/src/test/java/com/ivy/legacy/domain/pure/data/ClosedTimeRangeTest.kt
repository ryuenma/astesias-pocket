package com.ivy.wallet.domain.pure.data

import com.ivy.base.time.TimeProvider
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class ClosedTimeRangeTest {

    @Test
    fun `allTimeIvy includes today's noon-stamped transaction even when queried in the morning (#3398, #3352)`() {
        val zone = ZoneOffset.ofHours(7) // WIB (UTC+7)
        val morningLocal = LocalDateTime.of(2026, 10, 24, 9, 0)
        val noonTodayInstant = LocalDate.of(2026, 10, 24).atTime(LocalTime.of(12, 0)).atZone(zone).toInstant()

        val timeProvider = mockk<TimeProvider> {
            every { utcNow() } returns morningLocal.atZone(zone).toInstant()
            every { localNow() } returns morningLocal
            every { localDateNow() } returns morningLocal.toLocalDate()
            every { getZoneId() } returns zone
        }

        val range = ClosedTimeRange.allTimeIvy(timeProvider)
        range.to shouldBeGreaterThanOrEqualTo noonTodayInstant
    }
}

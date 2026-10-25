package com.ivy.ui.time.impl

import com.ivy.base.time.impl.StandardTimeConverter
import com.ivy.base.time.TimeProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AndroidDateTimePickerTest {

    @Test
    fun `selectedMillisToLocalDate interprets Material3 DatePicker UTC midnight without shifting day in negative offsets (#3495, #3646)`() {
        // Material3 DatePicker returns 2026-10-24T00:00:00Z when the user taps Oct 24
        val pickedUtcMidnightMillis = Instant.parse("2026-10-24T00:00:00Z").toEpochMilli()
        AndroidDateTimePicker.selectedMillisToLocalDate(pickedUtcMidnightMillis) shouldBe LocalDate.of(2026, 10, 24)
    }

    @Test
    fun `initialDatePickerMillis converts local date to UTC midnight so morning UTC+7 transactions highlight the right day`() {
        // 2026-10-24 05:00 WIB (UTC+7) is 2026-10-23T22:00:00Z in UTC
        val zone = ZoneOffset.ofHours(7)
        val timeProvider = mockk<TimeProvider> { every { getZoneId() } returns zone }
        val converter = StandardTimeConverter(timeProvider)
        val instant = Instant.parse("2026-10-23T22:00:00Z")

        val millis = AndroidDateTimePicker.initialDatePickerMillis(instant, converter)
        millis shouldBe Instant.parse("2026-10-24T00:00:00Z").toEpochMilli()
    }
}

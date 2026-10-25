package com.ivy.wallet.domain.pure.data

import com.ivy.base.time.TimeProvider
import com.ivy.legacy.utils.ivyMinTime
import java.time.Instant
import java.time.LocalTime

data class ClosedTimeRange(
    val from: Instant,
    val to: Instant,
) {
    companion object {
        fun allTimeIvy(
            timeProvider: TimeProvider,
        ): ClosedTimeRange = ClosedTimeRange(
            from = ivyMinTime(),
            to = timeProvider.localDateNow()
                .atTime(LocalTime.MAX)
                .atZone(timeProvider.getZoneId())
                .toInstant(),
        )

        fun to(to: Instant): ClosedTimeRange = ClosedTimeRange(
            from = ivyMinTime(),
            to = to
        )
    }
}

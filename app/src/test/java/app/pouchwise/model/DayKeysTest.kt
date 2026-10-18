package app.pouchwise.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DayKeysTest {
    private val zone: ZoneId = ZoneId.of("Europe/Amsterdam")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun midnightBoundaryByDefault() {
        val a = DayKeys.epochDay(ms(2026, 10, 17, 23, 59), 0, zone)
        val b = DayKeys.epochDay(ms(2026, 10, 18, 0, 1), 0, zone)
        assertEquals(a + 1, b)
    }

    @Test
    fun lateNightCountsTowardPreviousDayWhenDayStartsAt4() {
        val lateNight = DayKeys.epochDay(ms(2026, 10, 18, 2, 30), 4, zone)
        val evening = DayKeys.epochDay(ms(2026, 10, 17, 22, 0), 4, zone)
        val morning = DayKeys.epochDay(ms(2026, 10, 18, 9, 0), 4, zone)
        assertEquals(evening, lateNight)
        assertEquals(evening + 1, morning)
    }

    @Test
    fun startOfDayRoundTrips() {
        val day = DayKeys.epochDay(ms(2026, 3, 29, 12, 0), 4, zone) // DST change day in EU
        val start = DayKeys.startOfDayMs(day, 4, zone)
        assertEquals(day, DayKeys.epochDay(start, 4, zone))
        assertEquals(day - 1, DayKeys.epochDay(start - 1, 4, zone))
    }
}

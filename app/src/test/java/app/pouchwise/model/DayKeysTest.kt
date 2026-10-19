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
    fun dayStartStaysOnTheWallClockWhenClocksChange() {
        // 29 Mar 2026: clocks jump from 02:00 to 03:00 in Amsterdam; the day still starts at 04:00 local
        val day = LocalDateTime.of(2026, 3, 29, 12, 0).toLocalDate().toEpochDay()
        assertEquals(ms(2026, 3, 29, 4, 0), DayKeys.startOfDayMs(day, 4, zone))
        assertEquals(day, DayKeys.epochDay(ms(2026, 3, 29, 4, 30), 4, zone))
        assertEquals(day - 1, DayKeys.epochDay(ms(2026, 3, 29, 3, 30), 4, zone))
        // 25 Oct 2026: clocks fall back from 03:00 to 02:00
        val autumn = LocalDateTime.of(2026, 10, 25, 12, 0).toLocalDate().toEpochDay()
        assertEquals(ms(2026, 10, 25, 4, 0), DayKeys.startOfDayMs(autumn, 4, zone))
    }

    @Test
    fun aTimeStillAheadMeansTheDayBefore() {
        val now = ms(2026, 10, 19, 0, 20)
        val date = LocalDateTime.of(2026, 10, 19, 0, 0).toLocalDate()
        assertEquals(ms(2026, 10, 18, 23, 50), DayKeys.pastTimeMs(date, 23, 50, now, zone))
        assertEquals(ms(2026, 10, 19, 0, 10), DayKeys.pastTimeMs(date, 0, 10, now, zone))
        // across the autumn clock change it is one calendar day back, not 24 hours
        val afterChange = ms(2026, 10, 25, 10, 0)
        val oct25 = LocalDateTime.of(2026, 10, 25, 0, 0).toLocalDate()
        assertEquals(ms(2026, 10, 24, 23, 0), DayKeys.pastTimeMs(oct25, 23, 0, afterChange, zone))
    }

    @Test
    fun startOfDayRoundTrips() {
        val day = DayKeys.epochDay(ms(2026, 3, 29, 12, 0), 4, zone) // DST change day in EU
        val start = DayKeys.startOfDayMs(day, 4, zone)
        assertEquals(day, DayKeys.epochDay(start, 4, zone))
        assertEquals(day - 1, DayKeys.epochDay(start - 1, 4, zone))
    }
}

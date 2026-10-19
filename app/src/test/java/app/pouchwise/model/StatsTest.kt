package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    @Test
    fun aggregatesAndDayLevels() {
        val s = Settings(limitMode = LimitMode.COUNT, dailyLimitCount = 4)
        val day = 20_000L
        val start = DayKeys.startOfDayMs(day, 0)
        val entries = listOf(
            LogEntry(pouchTypeId = 1, timestamp = start + 1_000, strengthMg = 8.0),
            LogEntry(pouchTypeId = 1, timestamp = start + 2_000, strengthMg = 6.0),
            LogEntry(pouchTypeId = 1, timestamp = DayKeys.startOfDayMs(day + 2, 0) + 5, strengthMg = 4.0),
        )
        val stats = Stats.dailyStats(entries, s, day - 1, day + 2)
        assertEquals(4, stats.size)
        assertEquals(GardenLevel.EMPTY, GardenLevel.of(stats[0]))
        assertEquals(2, stats[1].count)
        assertEquals(14.0, stats[1].mg, 1e-9)
        assertEquals(GardenLevel.OK, GardenLevel.of(stats[1]))        // 2 of 4 = 50 %
        assertEquals(GardenLevel.CLEAN, GardenLevel.of(stats[2]))     // nothing that day
        assertEquals(GardenLevel.GOOD, GardenLevel.of(stats[3]))      // 1 of 4 = 25 %
        assertEquals(3, Stats.streak(stats))
    }

    @Test
    fun pastDaysAreJudgedByThePlanOfTheirDay() {
        val day = 20_000L
        val s = Settings(limitMode = LimitMode.COUNT, dailyLimitCount = 6) // the limit set today
        val entries = (0 until 8).map { LogEntry(pouchTypeId = 1, timestamp = DayKeys.startOfDayMs(day, 0) + it * 1_000L, strengthMg = 8.0, day = day) }
        val plans = listOf(
            PlanHistory.versionOf(s.copy(dailyLimitCount = 10), fromDay = 0L), // the limit back then
            PlanHistory.versionOf(s, fromDay = day + 5),
        )
        val stats = Stats.dailyStats(entries, s, day, day + 5, plans, today = day + 5)
        assertEquals(10, stats.first().limit)
        assertEquals(true, stats.first().withinLimit) // 8 of 10 then, not 8 of 6 now
        assertEquals(6, stats.last().limit)
        // without a recorded history every day falls back to today's plan
        assertEquals(false, Stats.dailyStats(entries, s, day, day, emptyList(), today = day + 5).first().withinLimit)
    }

    @Test
    fun anEntryStaysOnTheDayItWasLoggedInto() {
        val day = 20_000L
        // logged at 01:00 with the day starting at midnight
        val e = LogEntry(pouchTypeId = 1, timestamp = DayKeys.startOfDayMs(day, 0) + 3_600_000L, strengthMg = 8.0, day = day)
        val laterDayStart = Settings(dayStartHour = 4)
        assertEquals(day, DayKeys.of(e, laterDayStart.dayStartHour))
        assertEquals(1, Stats.dailyStats(listOf(e), laterDayStart, day - 1, day)[1].count)
        // a row without a stored day follows the current rules
        assertEquals(day - 1, DayKeys.of(e.copy(day = null), laterDayStart.dayStartHour))
    }

    @Test
    fun sevenLevelsCoverTheWholeRange() {
        fun lvl(count: Int, limit: Int) =
            GardenLevel.of(DayStat(0, count, 0.0, limit, LimitMode.COUNT, hasData = true))
        assertEquals(GardenLevel.CLEAN, lvl(0, 12))
        assertEquals(GardenLevel.GOOD, lvl(4, 12))       // 33 %
        assertEquals(GardenLevel.OK, lvl(8, 12))         // 66 %
        assertEquals(GardenLevel.AT_LIMIT, lvl(12, 12))  // 100 %
        assertEquals(GardenLevel.OVER, lvl(15, 12))      // 125 %
        assertEquals(GardenLevel.SAD, lvl(18, 12))       // 150 %
        assertEquals(GardenLevel.FAR_OVER, lvl(19, 12))
    }
}

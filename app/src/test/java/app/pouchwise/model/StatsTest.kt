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
        assertEquals(GardenLevel.GOOD, GardenLevel.of(stats[1]))      // 2 of 4 = 50 %
        assertEquals(GardenLevel.CLEAN, GardenLevel.of(stats[2]))     // nothing that day
        assertEquals(GardenLevel.GREAT, GardenLevel.of(stats[3]))     // 1 of 4 = 25 %
        assertEquals(3, Stats.streak(stats))
    }

    @Test
    fun sevenLevelsCoverTheWholeRange() {
        fun lvl(count: Int, limit: Int) =
            GardenLevel.of(DayStat(0, count, 0.0, limit, LimitMode.COUNT, hasData = true))
        assertEquals(GardenLevel.CLEAN, lvl(0, 8))
        assertEquals(GardenLevel.GREAT, lvl(2, 8))
        assertEquals(GardenLevel.GOOD, lvl(4, 8))
        assertEquals(GardenLevel.OK, lvl(6, 8))
        assertEquals(GardenLevel.AT_LIMIT, lvl(8, 8))
        assertEquals(GardenLevel.OVER, lvl(12, 8))
        assertEquals(GardenLevel.FAR_OVER, lvl(13, 8))
    }
}

package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsTest {
    @Test
    fun aggregatesAndGardenLevels() {
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
        assertEquals(GardenLevel.TREE, GardenLevel.of(stats[1]))
        assertEquals(GardenLevel.BLOOM, GardenLevel.of(stats[2]))
        assertEquals(3, Stats.streak(stats))
    }
}

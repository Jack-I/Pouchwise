package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitsTest {
    @Test
    fun fixedLimitWhenTaperOff() {
        val s = Settings(limitMode = LimitMode.COUNT, dailyLimitCount = 7)
        assertEquals(7, Limits.limitForDayRounded(s, 20_000))
    }

    @Test
    fun taperInterpolatesAndClamps() {
        val s = Settings(
            limitMode = LimitMode.MG, taperEnabled = true,
            taperStartDay = 100, taperStartValue = 53.0,
            taperTargetDay = 166, taperTargetValue = 27.0,
        )
        assertEquals(53.0, Limits.limitForDay(s, 90), 1e-9)
        assertEquals(27.0, Limits.limitForDay(s, 200), 1e-9)
        assertEquals(40.0, Limits.limitForDay(s, 133), 1e-9)
        assertEquals(40, Limits.limitForDayRounded(s, 133))
    }

    @Test
    fun exceedingRules() {
        val count = Settings(limitMode = LimitMode.COUNT)
        assertFalse(Limits.wouldExceed(count, 10, 9, 0.0, 8.0))
        assertTrue(Limits.wouldExceed(count, 10, 10, 0.0, 8.0))

        val mg = Settings(limitMode = LimitMode.MG)
        assertFalse(Limits.wouldExceed(mg, 43, 0, 35.0, 8.0))
        assertTrue(Limits.wouldExceed(mg, 43, 0, 36.0, 8.0))
        assertEquals(1, Limits.remainingPouches(mg, 43, 0, 35.0, 8.0))
        assertEquals(0, Limits.remainingPouches(mg, 43, 0, 36.0, 8.0))
        assertEquals(5, Limits.remainingPouches(mg, 43, 0, 0.0, 8.0))
    }

    @Test
    fun entriesBeyondTheLimitAreMarked() {
        val entries = (1..5).map { LogEntry(id = it.toLong(), pouchTypeId = 1, timestamp = it * 1000L, strengthMg = 8.0) }

        val byCount = Settings(limitMode = LimitMode.COUNT)
        assertEquals(setOf(4L, 5L), Limits.overLimitEntryIds(entries, byCount, 3))
        assertEquals(emptySet<Long>(), Limits.overLimitEntryIds(entries, byCount, 5))

        val byMg = Settings(limitMode = LimitMode.MG)
        // 8, 16, 24, 32, 40 mg cumulative against a 24 mg limit: the 4th and 5th are over.
        assertEquals(setOf(4L, 5L), Limits.overLimitEntryIds(entries, byMg, 24))
    }
}

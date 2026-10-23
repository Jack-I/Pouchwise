package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
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
        assertEquals(1.0, Limits.left(LimitMode.COUNT, 10, 9, 0.0), 0.0)
        assertEquals(-1.0, Limits.left(LimitMode.COUNT, 10, 11, 0.0), 0.0)
        assertEquals(0.0, Limits.left(LimitMode.MG, 49, 0, 49.03), 0.0)  // a rounding hair over is "reached"
        assertEquals(1.3, Limits.left(LimitMode.MG, 49, 0, 47.7), 1e-9)

        val mg = Settings(limitMode = LimitMode.MG)
        assertEquals(1, Limits.remainingPouches(mg, 43, 0, 35.0, 8.0))
        assertEquals(0, Limits.remainingPouches(mg, 43, 0, 36.0, 8.0))
        assertEquals(5, Limits.remainingPouches(mg, 43, 0, 0.0, 8.0))
    }

    @Test
    fun switchingTheUnitKeepsThePlan() {
        val count = Settings(
            limitMode = LimitMode.COUNT, dailyLimitCount = 10,
            taperEnabled = true, taperStartDay = 100, taperStartValue = 10.0, taperTargetDay = 160, taperTargetValue = 4.0,
        )
        val mg = Limits.convertMode(count, LimitMode.MG, 8.0)
        assertEquals(LimitMode.MG, mg.limitMode)
        assertTrue(mg.taperEnabled)
        assertEquals(80.0, mg.dailyLimitMg, 1e-9)
        assertEquals(80.0, mg.taperStartValue, 1e-9)
        assertEquals(32.0, mg.taperTargetValue, 1e-9)

        val back = Limits.convertMode(mg, LimitMode.COUNT, 8.0)
        assertEquals(10, back.dailyLimitCount)
        assertEquals(10.0, back.taperStartValue, 1e-9)
        assertEquals(4.0, back.taperTargetValue, 1e-9)
        assertEquals(count, Limits.convertMode(count, LimitMode.COUNT, 8.0)) // same mode: untouched
    }

    @Test
    fun switchingThereAndBackDoesNotDrift() {
        val mg = Settings(limitMode = LimitMode.MG, dailyLimitMg = 15.0, taperEnabled = true, taperStartValue = 60.0, taperTargetValue = 30.0)
        val count = Limits.convertMode(mg, LimitMode.COUNT, 6.5)
        assertEquals(2, count.dailyLimitCount)
        val back = Limits.convertMode(count, LimitMode.MG, 6.5)
        assertEquals(15.0, back.dailyLimitMg, 1e-9)
        assertEquals(60.0, back.taperStartValue, 1e-9)
        assertEquals(30.0, back.taperTargetValue, 1e-9)
        // A value changed in between is converted, not restored: 3 × 6.5 = 19.5 → 20 mg.
        assertEquals(20.0, Limits.convertMode(count.copy(taperTargetValue = 3.0), LimitMode.MG, 6.5).taperTargetValue, 1e-9)
        // A zero limit stays zero.
        assertEquals(0, Limits.convertMode(mg.copy(dailyLimitMg = 0.0), LimitMode.COUNT, 6.5).dailyLimitCount)
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

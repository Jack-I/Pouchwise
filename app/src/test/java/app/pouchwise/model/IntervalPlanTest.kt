package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class IntervalPlanTest {
    private val zone = ZoneId.systemDefault()
    private val s = Settings(bodyWeightKg = 72.0)
    private var nextId = 1L

    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun pouch(ms: Long) =
        LogEntry(id = nextId++, pouchTypeId = 1, timestamp = ms, strengthMg = 6.0, pouchMinutes = 30, day = DayKeys.epochDay(ms, 0))

    @Test
    fun usualLevelLeavesOutEachDaysFirstPouchAndTakesTheMedian() {
        // Mon 5 .. Wed 7 October, four pouches a day.
        val entries = (5..7).flatMap { d -> listOf(8, 10, 12, 14).map { h -> pouch(at(d, h)) } }
        val levels = IntervalPlan.levelsAtPouches(entries, s, at(1, 0), at(20, 0))
        assertEquals(9, levels.size)
        assertTrue(levels.all { it > 0.3 })
        assertEquals(levels.sorted()[4], IntervalPlan.usualLevel(entries, s, at(1, 0), at(20, 0))!!, 1e-12)
        // One day gives only three pouches after the first: too little to go by.
        assertNull(IntervalPlan.usualLevel(entries.take(4), s, at(1, 0), at(20, 0)))
    }

    @Test
    fun keepUsesTheLastTwoWeeksAndLowerAQuarterLessFromBeforeMonday() {
        val entries = (5..21).flatMap { d -> listOf(8, 10, 12, 14).map { h -> pouch(at(d, h)) } }
        val now = at(21, 15)
        val keep = IntervalPlan.threshold(entries, s.copy(intervalMode = IntervalMode.KEEP), now)!!
        assertEquals(IntervalPlan.usualLevel(entries, s, now - IntervalPlan.WINDOW_MS, now)!!, keep, 1e-12)
        val monday = at(19, 0)
        assertEquals(monday, IntervalPlan.weekStartMs(now, 0))
        val lower = IntervalPlan.threshold(entries, s.copy(intervalMode = IntervalMode.LOWER), now)!!
        assertEquals(IntervalPlan.usualLevel(entries, s, monday - IntervalPlan.WINDOW_MS, monday)!! * 0.75, lower, 1e-12)
        assertNull(IntervalPlan.threshold(entries, s, now))
    }

    @Test
    fun lowerNeverGoesBelowTheLevelWhereReceptorsAreHalfFree() {
        // Two pouches a day twelve hours apart: the second goes in with next to nothing left.
        val entries = (1..18).flatMap { d -> listOf(8, 20).map { h -> pouch(at(d, h)) } }
        val lower = IntervalPlan.threshold(entries, s.copy(intervalMode = IntervalMode.LOWER), at(21, 9))!!
        assertEquals(IntervalPlan.restLevelMg(s), lower, 1e-12)
        // 0.87 ng/mL × 72 kg × 2.6 L/kg
        assertEquals(0.163, IntervalPlan.restLevelMg(s), 0.001)
    }

    @Test
    fun nextOnPlanIsWhenTheForecastStaysBelowTheLevel() {
        val now = at(10, 12)
        val doses = listOf(PouchTiming.toDose(pouch(now - 30 * 60_000L), s))
        val next = IntervalPlan.nextOnPlanMs(doses, s.pk, 0.5, now)!!
        assertTrue(Pharmacokinetics.levelsAt(next, doses, s.pk).nicotineMg < 0.5)
        assertTrue(Pharmacokinetics.levelsAt(next - 60_000L, doses, s.pk).nicotineMg >= 0.5)
        // Already below and staying there: nothing to wait for.
        assertNull(IntervalPlan.nextOnPlanMs(doses, s.pk, 5.0, now))
    }

    @Test
    fun takingAPouchOutEarlyBringsTheNextOneForward() {
        val start = at(10, 12)
        val now = start + 10 * 60_000L
        val stillIn = pouch(start)
        val out = PouchTiming.finish(stillIn, s, now)
        val whileIn = IntervalPlan.nextOnPlanMs(listOf(PouchTiming.toDose(stillIn, s)), s.pk, 0.5, now)!!
        val afterOut = IntervalPlan.nextOnPlanMs(listOf(PouchTiming.toDose(out, s)), s.pk, 0.5, now)!!
        assertTrue(afterOut > now)
        assertTrue(afterOut < whileIn)
    }

    @Test
    fun receptorsRestWhileNicotineIsLow() {
        val from = at(10, 0)
        val to = at(10, 12)
        assertEquals(to - from, IntervalPlan.restMs(emptyList(), s.pk, IntervalPlan.restLevelMg(s), from, to))
        // A pouch at 08:00 keeps nicotine above the rest level until well past noon.
        val doses = listOf(PouchTiming.toDose(pouch(at(10, 8)), s))
        assertEquals(8 * 3_600_000L.toDouble(), IntervalPlan.restMs(doses, s.pk, IntervalPlan.restLevelMg(s), from, to).toDouble(), 5 * 60_000.0)
    }

    @Test
    fun aPouchIsEarlyWhenNicotineWasStillAboveItsPlanLevel() {
        val first = pouch(at(10, 8)).copy(planLevelMg = 0.5)
        val soon = pouch(at(10, 9)).copy(planLevelMg = 0.5)
        val later = pouch(at(10, 16)).copy(planLevelMg = 0.5)
        val day = listOf(first, soon, later)
        assertEquals(setOf(soon.id), IntervalPlan.earlyIds(day, day, s))
        // Without a plan level nothing is early.
        val unplanned = day.map { it.copy(planLevelMg = null) }
        assertTrue(IntervalPlan.earlyIds(unplanned, unplanned, s).isEmpty())
    }
}

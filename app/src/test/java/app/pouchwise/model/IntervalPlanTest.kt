package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.min

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
    fun keepUsesTheTwoWeeksBeforeTodayAndLowerFifteenPercentLessFromBeforeMonday() {
        val entries = (1..21).flatMap { d -> listOf(8, 10, 12, 14).map { h -> pouch(at(d, h)) } }
        val now = at(21, 15)
        val cap = IntervalPlan.basis(entries, s, now).capMg!!
        val keep = IntervalPlan.threshold(entries, s.copy(intervalMode = IntervalMode.KEEP), now)!!
        val dayStart = DayKeys.startOfDayMs(DayKeys.epochDay(now, s.dayStartHour), s.dayStartHour)
        assertEquals(min(IntervalPlan.usualLevel(entries, s, dayStart - IntervalPlan.WINDOW_MS, dayStart)!!, cap), keep, 1e-12)
        val monday = at(19, 0)
        assertEquals(monday, IntervalPlan.weekStartMs(now, 0))
        val lower = IntervalPlan.threshold(entries, s.copy(intervalMode = IntervalMode.LOWER), now)!!
        assertEquals(min(IntervalPlan.usualLevel(entries, s, monday - IntervalPlan.WINDOW_MS, monday)!!, cap) * IntervalPlan.LOWER_SHARE, lower, 1e-12)
        assertNull(IntervalPlan.threshold(entries, s, now))
        // Without two full weeks before Monday, Lower is 17 % below today's Hold.
        val lately = entries.filter { it.timestamp >= at(10, 0) }
        val keepLately = IntervalPlan.threshold(lately, s.copy(intervalMode = IntervalMode.KEEP), now)!!
        assertEquals(keepLately * IntervalPlan.LOWER_SHARE, IntervalPlan.threshold(lately, s.copy(intervalMode = IntervalMode.LOWER), now)!!, 1e-12)
    }

    @Test
    fun theCapIsTheMedianOverEveryPouchNotTheMostLoggedKind() {
        fun kind(type: Long, mg: Double, k: Double, minutes: Int) = { ms: Long ->
            LogEntry(
                id = nextId++, pouchTypeId = type, timestamp = ms, strengthMg = mg, releaseK = k, pouchMinutes = 45,
                endTimestamp = ms + minutes * 60_000L, day = DayKeys.epochDay(ms, 0),
            )
        }
        val strong = kind(1, 6.5, 0.103, 40)
        val medium = kind(2, 6.0, 0.062, 40)
        val weak = kind(3, 4.0, 0.062, 20)
        // Two weeks of three weak, two medium and two strong pouches a day: the weak kind is logged most.
        val entries = (5..18).flatMap { d ->
            listOf(weak(at(d, 8)), strong(at(d, 10)), medium(at(d, 12)), weak(at(d, 14)), strong(at(d, 16)), medium(at(d, 18)), weak(at(d, 20)))
        }
        val cap = IntervalPlan.basis(entries, s, at(19, 9)).capMg!!
        assertEquals(IntervalPlan.leftAfterCapMg(medium(0L), s), cap, 1e-12)
        assertTrue(cap > IntervalPlan.leftAfterCapMg(weak(0L), s) * 1.5)
        assertTrue(cap < IntervalPlan.leftAfterCapMg(strong(0L), s))
    }

    @Test
    fun backToBackPouchesAreHeldNoHigherThanATypicalPouchTwoHoursOn() {
        // Two weeks of a pouch every half hour from 08:00 to 14:00: the plan holds no more than one of them leaves after two hours.
        val entries = (5..20).flatMap { d -> (0..12).map { i -> pouch(at(d, 8) + i * 30 * 60_000L) } }
        val now = at(21, 9)
        val one = PouchTiming.toDose(pouch(0L), s)
        val cap = Pharmacokinetics.levelsAt(2 * 3_600_000L, listOf(one), s.pk).nicotineMg
        val basis = IntervalPlan.basis(entries, s, now)
        assertEquals(cap, basis.capMg!!, 1e-12)
        assertTrue(basis.usualMg!! > 2 * cap)
        assertEquals(cap, IntervalPlan.threshold(entries, s.copy(intervalMode = IntervalMode.KEEP), now)!!, 1e-12)
        assertEquals(cap * IntervalPlan.LOWER_SHARE, IntervalPlan.threshold(entries, s.copy(intervalMode = IntervalMode.LOWER), now)!!, 1e-12)
        // After one such pouch from nothing, the next is due two hours after it went in.
        val start = at(22, 10)
        val next = IntervalPlan.nextOnPlanMs(listOf(one.copy(timeMs = start)), s.pk, cap, start + 60_000L)!!
        assertEquals((start + 2 * 3_600_000L).toDouble(), next.toDouble(), 60_000.0)
        // The Plan card's times: two hours with Hold; with Lower, as long again as nicotine takes to lose 17 % (half-life 2 h).
        val typical = basis.typical!!
        assertEquals(2 * 3_600_000.0, IntervalPlan.afterAloneMs(typical, cap, s)!!.toDouble(), 60_000.0)
        val lowerMs = (2 + 2 * ln(1 / IntervalPlan.LOWER_SHARE) / ln(2.0)) * 3_600_000.0
        assertEquals(lowerMs, IntervalPlan.afterAloneMs(typical, cap * IntervalPlan.LOWER_SHARE, s)!!.toDouble(), 60_000.0)
    }

    @Test
    fun theLevelHoldsForTheWholeDay() {
        // Two days of a pouch every four hours, then a morning of one an hour: today's pouches do not move today's level.
        val before = (19..20).flatMap { d -> listOf(8, 12, 16, 20).map { h -> pouch(at(d, h)) } }
        val today = listOf(9, 10, 11, 12).map { h -> pouch(at(21, h)) }
        val keep = s.copy(intervalMode = IntervalMode.KEEP)
        assertEquals(IntervalPlan.threshold(before, keep, at(21, 8))!!, IntervalPlan.threshold(before + today, keep, at(21, 22))!!, 1e-12)
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

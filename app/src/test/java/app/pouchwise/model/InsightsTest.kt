package app.pouchwise.model

import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class InsightsTest {
    private val zone = ZoneId.systemDefault()
    private var nextId = 1L

    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun day(d: Int) = LocalDate.of(2026, 10, d).toEpochDay()
    private fun pouch(ms: Long, price: Double? = 20.0) = LogEntry(
        id = nextId++, pouchTypeId = 1, timestamp = ms, strengthMg = 6.0, pouchMinutes = 30,
        day = DayKeys.epochDay(ms, 0), pricePerPouch = price,
    )

    @Test
    fun spendingCountsLoggedPricesAndSavesAgainstTheBaseline() {
        // Thu 15 .. Tue 20 October 2026; today is Tuesday the 20th.
        val entries = listOf(
            pouch(at(15, 9)), pouch(at(15, 12)),
            pouch(at(19, 9)), pouch(at(19, 13)), pouch(at(19, 18)),
            pouch(at(20, 9)), pouch(at(20, 11), price = null),
        )
        val sum = Spending.summary(entries, Settings(baselinePerDay = 4), day(20))
        assertEquals(80.0, sum.week, 1e-9)
        assertEquals(120.0, sum.month, 1e-9)
        assertEquals(20.0, sum.perPouch!!, 1e-9)
        // Finished days Thu 15 .. Mon 19: 5 × 4 − 5 used = 15 pouches not bought, at 20 each.
        assertEquals(300.0, sum.saved!!, 1e-9)
        assertEquals(day(15), sum.sinceDay)
        assertTrue(sum.unpriced)
        // No baseline, no savings.
        assertNull(Spending.summary(entries, Settings(), day(20)).saved)
    }

    @Test
    fun patternsByHourWeekdayFirstPouchGapsAndTimeInMouth() {
        val entries = listOf(
            pouch(at(19, 8)), pouch(at(19, 10)), pouch(at(19, 13)),
            pouch(at(20, 9)), pouch(at(20, 10)),
        ).map { it.copy(endTimestamp = it.timestamp + 20 * 60_000L) }
        val p = Patterns.summary(entries, Settings(), day(20), at(20, 23), zone)
        assertEquals(1, p.byHour[8])
        assertEquals(1, p.byHour[9])
        assertEquals(2, p.byHour[10])
        assertEquals(1, p.byHour[13])
        assertEquals(3.0, p.byWeekday[0], 1e-9)
        // Tuesday is today and still running: it counts once it is over.
        assertEquals(0.0, p.byWeekday[1], 1e-9)
        assertEquals(0.0, p.byWeekday[2], 1e-9)
        assertEquals(listOf(8 * 60, 9 * 60), p.firstPouch)
        assertEquals(8 * 60 + 30, p.firstNow)
        // Two days are too few for a trend.
        assertNull(p.firstShift)
        // Breaks after 20-minute pouches: 1 h 40 min and 2 h 40 min on Monday, 40 min on Tuesday.
        assertEquals(100 * 60_000L, p.typicalBreakMs)
        assertEquals(20 * 60_000L, p.avgInMouthMs)
    }

    @Test
    fun feltCountsEachPouchOnceByItsStrongestAnswerPerWeekAndForTheDaysFirst() {
        // Today is Wednesday 4 November; the 30 days go back to Tuesday 6 October.
        val old = pouch(at(5, 9))
        val monFirst = pouch(at(19, 8))
        val monLater = pouch(at(19, 12))
        val unanswered = pouch(at(20, 9))
        val nextWeek = pouch(LocalDateTime.of(2026, 11, 2, 10, 0).atZone(zone).toInstant().toEpochMilli())
        fun mark(e: LogEntry, felt: Felt) = FeltMark(entryId = e.id, level = felt.level, atMs = e.timestamp + 600_000L)
        val marks = listOf(
            mark(old, Felt.HIT), mark(monFirst, Felt.A_BIT), mark(monFirst, Felt.HIT),
            mark(monLater, Felt.HIT), mark(monLater, Felt.NOTHING), mark(nextWeek, Felt.OVERWHELM),
        ).groupBy { it.entryId }
        val today = LocalDate.of(2026, 11, 4).toEpochDay()
        val f = FeltPatterns.summary(listOf(old, monFirst, monLater, unanswered, nextWeek), marks, Settings(), today)
        assertEquals(3, f.answered)
        assertEquals(4, f.pouches)
        assertEquals(listOf(day(19), LocalDate.of(2026, 11, 2).toEpochDay()), f.weeks.map { it.monday })
        assertEquals(listOf(0, 0, 2, 0), f.weeks[0].counts)
        assertEquals(listOf(0, 0, 0, 1), f.weeks[1].counts)
        // Monday the 19th's first pouch and the 2nd's only one; the 20th's first has no answer.
        assertEquals(listOf(0, 0, 1, 1), f.firstOfDay)
    }

    private val hour = 3_600_000L
    private val oneAm = Settings(dayStartHour = 1, bodyWeightKg = 72.0)
    private fun pouchFiled(ms: Long) = LogEntry(
        id = nextId++, pouchTypeId = 1, timestamp = ms, strengthMg = 6.0, pouchMinutes = 30, day = DayKeys.epochDay(ms, 1),
    )

    @Test
    fun restLeavesOutTheFirstDayAndCountsTodayUpToNow() {
        // The 00:30 pouch on the 12th belongs to the 11th, whose day runs to 01:00.
        val entries = listOf(pouchFiled(at(10, 22)), pouchFiled(at(12, 0, 30)))
        assertEquals(day(11)..day(13), ReceptorRest.days(entries, oneAm, day(13)))
        val doses = entries.map { PouchTiming.toDose(it, oneAm) }
        val rest = ReceptorRest.byDay(doses, oneAm, day(11)..day(13), at(13, 7))
        assertEquals(listOf(day(11), day(12), day(13)), rest.map { it.epochDay })
        assertEquals(listOf(false, false, true), rest.map { it.running })
        // Each morning waits for the night's pouch to clear.
        rest.take(2).forEach { assertTrue("${it.restMs / 60_000} min", it.restMs in 16 * hour until 24 * hour) }
        assertEquals(6 * hour, rest[2].restMs)
    }

    @Test
    fun aDayWithoutPouchesRestsWholeAndAClockChangeDayIsLonger() {
        // Cyprus turns the clocks back on Sunday 25 October 2026.
        val rest = ReceptorRest.byDay(emptyList(), oneAm, day(24)..day(25), Long.MAX_VALUE, ZoneId.of("Asia/Nicosia"))
        assertEquals(listOf(24 * hour, 25 * hour), rest.map { it.restMs })
    }

    @Test
    fun weekAveragesUseFinishedDaysOnly() {
        val days = listOf(
            RestDay(day(18), 6 * hour, running = false),
            RestDay(day(19), 2 * hour, running = false),
            RestDay(day(20), 4 * hour, running = false),
            RestDay(day(21), 1 * hour, running = true),
        )
        assertEquals(listOf(RestWeek(day(12), 6 * hour), RestWeek(day(19), 3 * hour)), ReceptorRest.weeks(days))
        assertEquals(emptyList<RestWeek>(), ReceptorRest.weeks(listOf(RestDay(day(19), hour, running = true))))
    }

    @Test
    fun noDayToShowWithoutAnEarlierDay() {
        assertTrue(ReceptorRest.days(listOf(pouchFiled(at(13, 9))), oneAm, day(13)).isEmpty())
        assertTrue(ReceptorRest.days(emptyList(), oneAm, day(13)).isEmpty())
    }
}

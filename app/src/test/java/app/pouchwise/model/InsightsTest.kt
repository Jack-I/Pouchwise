package app.pouchwise.model

import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
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
        assertEquals(20.0, sum.today, 1e-9)
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
        assertEquals(2.0, p.byWeekday[1], 1e-9)
        assertEquals(0.0, p.byWeekday[2], 1e-9)
        assertEquals(listOf(8 * 60, 9 * 60), p.firstPouch)
        assertEquals(8 * 60 + 30, p.firstNow)
        // Two days are too few for a trend.
        assertNull(p.firstShift)
        // Gaps of 2 h and 3 h on Monday and 1 h on Tuesday.
        assertEquals(2 * 3_600_000L, p.typicalGapMs)
        assertEquals(20 * 60_000L, p.avgInMouthMs)
    }

    @Test
    fun theBaselineIsReadBackFromTheFirstPlan() {
        val mgPlan = PlanHistory.versionOf(Settings(limitMode = LimitMode.MG, taperEnabled = true, taperStartValue = 60.0), 0)
        assertEquals(10, PlanHistory.baselinePerDay(mgPlan, PouchType(name = "ZYN", strengthMg = 6.0)))
        val countPlan = PlanHistory.versionOf(Settings(limitMode = LimitMode.COUNT, dailyLimitCount = 12), 0)
        assertEquals(12, PlanHistory.baselinePerDay(countPlan, null))
        assertNull(PlanHistory.baselinePerDay(null, null))
    }
}

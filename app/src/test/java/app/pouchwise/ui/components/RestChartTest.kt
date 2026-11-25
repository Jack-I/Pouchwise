package app.pouchwise.ui.components

import app.pouchwise.model.RestWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RestChartTest {
    private fun day(month: Int, d: Int) = LocalDate.of(2026, month, d).toEpochDay()

    @Test
    fun theRunningWeeksLineStopsAtToday() {
        // Days from Monday 19 October; today is Tuesday 17 November.
        val first = day(10, 19)
        val today = (day(11, 17) - first).toInt()
        assertEquals(28..29, weekSlots(day(11, 16), first, lastIdx = today))
        assertEquals(21..27, weekSlots(day(11, 9), first, lastIdx = today))
    }

    @Test
    fun aLabelWiderThanItsLineIsLeftOut() {
        assertEquals(107f, labelLeft(100f, 140f, 33f)!!, 0f)
        assertNull(labelLeft(100f, 120f, 33f))
    }

    @Test
    fun theDescriptionReadsTheLastTwoWeeks() {
        val weeks = listOf(RestWeek(day(11, 2), 1L), RestWeek(day(11, 9), 2L), RestWeek(day(11, 16), 3L))
        val labels = mapOf(1L to "2 h 28", 2L to "2 h 15", 3L to "2 h 36")
        val range = { from: Long, to: Long -> "${LocalDate.ofEpochDay(from).dayOfMonth}-${LocalDate.ofEpochDay(to).dayOfMonth}" }
        assertEquals("9-15: 2 h 15; 16-22: 2 h 36", restDescription(weeks, { labels.getValue(it.avgMs) }, range))
        assertEquals("", restDescription(emptyList(), { "" }, range))
    }

    @Test
    fun theFirstWeeksLineStartsAtTheFirstDay() {
        // Days from Thursday 22 October; its week's Monday is three slots before the first.
        assertEquals(0..3, weekSlots(day(10, 19), day(10, 22), lastIdx = 5))
    }

    @Test
    fun theLeftMonthSitsOverTheFirstDayWhenTheDaysStartFurtherRight() {
        assertEquals(36f, leftMonthX(padL = 36f, firstSlotLeft = -60f), 0f)
        assertEquals(190f, leftMonthX(padL = 36f, firstSlotLeft = 190f), 0f)
    }

    @Test
    fun fewerDaysThanFitStillPutTodayWhereItBelongs() {
        // Five days and room for twelve: today at the right edge, nothing to drag.
        assertEquals(-7f..-7f, stripBounds(days = 5, shown = 12f, place = 5 - 12f))
        // Forty days: from the first one at the left edge to today at the right.
        assertEquals(0f..28f, stripBounds(days = 40, shown = 12f, place = 28f))
    }
}

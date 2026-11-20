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
        val range = { from: Long, to: Long -> "${LocalDate.ofEpochDay(from).dayOfMonth}-${LocalDate.ofEpochDay(to).dayOfMonth}" }
        assertEquals("9-15: 2 h 15; 16-22: 2 h 36", restDescription(weeks, listOf("2 h 28", "2 h 15", "2 h 36"), range))
        assertEquals("", restDescription(emptyList(), emptyList(), range))
    }
}

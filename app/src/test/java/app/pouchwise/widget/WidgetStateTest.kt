package app.pouchwise.widget

import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.model.LimitMode
import app.pouchwise.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    @Test
    fun coverButtonsAreTheMostUsedPouchesInTheirOwnOrder() {
        fun type(id: Long, order: Int) = PouchType(id = id, name = "P$id", strengthMg = 6.0, sortOrder = order)
        val active = listOf(type(1, 0), type(2, 1), type(3, 2), type(4, 3), type(5, 4))
        // Five pouches, three in use: the three used, in the order of "Your pouches", not by count.
        assertEquals(listOf(2L, 4L, 5L), quickPicks(active, mapOf(5L to 9, 2L to 4, 4L to 7, 1L to 1)).map { it.id })
        // Fewer than three used lately: the rest are filled in from the top of the list.
        assertEquals(listOf(1L, 2L, 4L), quickPicks(active, mapOf(4L to 3)).map { it.id })
        // Nothing logged yet, or fewer pouches than buttons.
        assertEquals(listOf(1L, 2L, 3L), quickPicks(active, emptyMap()).map { it.id })
        assertEquals(listOf(1L), quickPicks(active.take(1), mapOf(1L to 2)).map { it.id })
    }

    @Test
    fun ringFillsTowardsTheLimitAndFlagsOveruse() {
        val half = WidgetState(LimitMode.COUNT, count = 5, mg = 40.0, limit = 10, hasTypes = true)
        assertEquals(0.5f, half.ratio, 1e-6f)
        assertFalse(half.over)

        val exact = half.copy(count = 10)
        assertEquals(1f, exact.ratio, 1e-6f)
        assertFalse(exact.over)

        val over = half.copy(count = 12)
        assertEquals(1f, over.ratio, 1e-6f)
        assertTrue(over.over)
    }

    @Test
    fun mgModeUsesMilligramsAndZeroLimitCountsAnythingAsFull() {
        val mg = WidgetState(LimitMode.MG, count = 3, mg = 24.0, limit = 60, hasTypes = false)
        assertEquals(0.4f, mg.ratio, 1e-6f)
        assertFalse(mg.over)

        val noLimit = WidgetState(LimitMode.COUNT, count = 1, mg = 8.0, limit = 0, hasTypes = false)
        assertEquals(1f, noLimit.ratio, 1e-6f)
        assertTrue(noLimit.over)
        assertEquals(0f, noLimit.copy(count = 0, mg = 0.0).ratio, 1e-6f)
    }

    @Test
    fun pouchesInSkipsRemovedAndExpiredOnesOldestFirst() {
        val s = Settings() // pouch time 30 min
        val now = 10_000_000_000L
        val min = 60_000L
        val oldest = LogEntry(id = 1, pouchTypeId = 1, timestamp = now - 20 * min, strengthMg = 6.0)
        val latest = LogEntry(id = 2, pouchTypeId = 2, timestamp = now - 5 * min, strengthMg = 4.0)
        val expired = LogEntry(id = 3, pouchTypeId = 1, timestamp = now - 40 * min, strengthMg = 6.0)
        val takenOut = LogEntry(id = 4, pouchTypeId = 1, timestamp = now - 10 * min, strengthMg = 6.0, endTimestamp = now - 2 * min)
        assertEquals(listOf(oldest, latest), pouchesIn(listOf(latest, expired, oldest, takenOut), s, now))
        assertTrue(pouchesIn(listOf(expired, takenOut), s, now).isEmpty())

        val loggedWithAnHour = expired.copy(id = 5, pouchMinutes = 60) // logged before the setting dropped to 30
        assertEquals(listOf(loggedWithAnHour), pouchesIn(listOf(loggedWithAnHour, expired), s, now))
    }

    @Test
    fun widgetLabelIsTheBrandWhenThereIsOne() {
        assertEquals("ZYN", widgetLabel(PouchType(name = "ZYN Cool Mint", strengthMg = 6.0, brand = "ZYN")))
        assertEquals("Vont", widgetLabel(PouchType(name = "Vont", strengthMg = 7.0, brand = " ")))
        assertEquals("?", widgetLabel(null))
    }
}

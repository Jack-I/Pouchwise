package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PouchTimingTest {
    private val s = Settings() // default pouch time 30 min
    private val start = 1_000_000_000L
    private val min = 60_000L

    @Test
    fun activeUntilAutoRemoval() {
        val e = LogEntry(pouchTypeId = 1, timestamp = start, strengthMg = 8.0)
        assertTrue(PouchTiming.isActive(e, s, start + 10 * min))
        assertTrue(PouchTiming.isActive(e, s, start + 29 * min))
        assertFalse(PouchTiming.isActive(e, s, start + 30 * min))
        assertFalse(PouchTiming.isActive(e, s, start - 1))
        assertEquals(30 * min, PouchTiming.effectiveDurationMs(e, s))
    }

    @Test
    fun removingEarlyShortensTheDose() {
        val e = LogEntry(pouchTypeId = 1, timestamp = start, strengthMg = 8.0)
        val finished = PouchTiming.finish(e, s, start + 12 * min)
        assertEquals(start + 12 * min, finished.endTimestamp)
        assertFalse(PouchTiming.isActive(finished, s, start + 13 * min))
        assertEquals(12 * min, PouchTiming.effectiveDurationMs(finished, s))
        assertEquals(12 * min, PouchTiming.toDose(finished, s).durationMs)
    }

    @Test
    fun finishIsClampedBetweenOneMinuteAndPouchTime() {
        val e = LogEntry(pouchTypeId = 1, timestamp = start, strengthMg = 8.0)
        assertEquals(start + min, PouchTiming.finish(e, s, start + 5_000).endTimestamp)
        assertEquals(start + 30 * min, PouchTiming.finish(e, s, start + 90 * min).endTimestamp)
    }
}

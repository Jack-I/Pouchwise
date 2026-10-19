package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

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
    fun absorbedGrowsWhileInAndFreezesAfterRemoval() {
        val e = LogEntry(pouchTypeId = 1, timestamp = start, strengthMg = 10.0)
        assertEquals(0.0, PouchTiming.absorbedMg(e, s, start - 1), 1e-12)
        val at10 = PouchTiming.absorbedMg(e, s, start + 10 * min)
        val at20 = PouchTiming.absorbedMg(e, s, start + 20 * min)
        val at30 = PouchTiming.absorbedMg(e, s, start + 30 * min)
        val at90 = PouchTiming.absorbedMg(e, s, start + 90 * min)
        assertTrue(0.0 < at10 && at10 < at20 && at20 < at30)
        assertEquals(3.0, at30, 1e-9)          // full pouch time: 30 % of 10 mg
        assertEquals(at30, at90, 1e-12)        // nothing more after the pouch time

        val removed = PouchTiming.finish(e, s, start + 15 * min)
        val expected = 10.0 * s.pk.absorptionFraction * Pharmacokinetics.releasedShare(15.0, Brands.averageK)
        assertEquals(expected, PouchTiming.absorbedMg(removed, s, start + 15 * min), 1e-9)
        assertEquals(expected, PouchTiming.absorbedMg(removed, s, start + 5 * 60 * min), 1e-9)
    }

    @Test
    fun finishIsClampedBetweenOneMinuteAndPouchTime() {
        val e = LogEntry(pouchTypeId = 1, timestamp = start, strengthMg = 8.0)
        assertEquals(start + min, PouchTiming.finish(e, s, start + 5_000).endTimestamp)
        assertEquals(start + 30 * min, PouchTiming.finish(e, s, start + 90 * min).endTimestamp)
    }

    @Test
    fun aLoggedPouchKeepsItsOwnTimeWhenTheSettingChanges() {
        val e = LogEntry(pouchTypeId = 1, timestamp = start, strengthMg = 8.0, pouchMinutes = 30)
        val longer = Settings(pk = PkParams(absorptionMinutes = 60))
        val shorter = Settings(pk = PkParams(absorptionMinutes = 20))
        for (changed in listOf(longer, shorter)) {
            assertEquals(30 * min, PouchTiming.effectiveDurationMs(e, changed))
            assertTrue(PouchTiming.isActive(e, changed, start + 25 * min))
            assertFalse(PouchTiming.isActive(e, changed, start + 30 * min))
            assertEquals(PouchTiming.absorbedMg(e, s, start + 25 * min), PouchTiming.absorbedMg(e, changed, start + 25 * min), 1e-12)
        }
        val takenOut = PouchTiming.finish(e, s, start + 25 * min)
        assertEquals(25 * min, PouchTiming.effectiveDurationMs(takenOut, shorter)) // not cut to the new 20 min
    }

    @Test
    fun lastOutIsThePouchThatCameOutLastNotTheOneThatWentInLast() {
        val a = LogEntry(id = 1, pouchTypeId = 1, timestamp = start, strengthMg = 8.0, pouchMinutes = 30)                // out by itself at +30
        val b = LogEntry(id = 2, pouchTypeId = 1, timestamp = start + 5 * min, strengthMg = 8.0, endTimestamp = start + 6 * min, pouchMinutes = 30)
        assertEquals(start + 30 * min, PouchTiming.lastOutMs(listOf(a, b), s, start + 50 * min))
        // while A is still in, the last one out is B
        assertEquals(start + 6 * min, PouchTiming.lastOutMs(listOf(a, b), s, start + 20 * min))
        assertEquals(null, PouchTiming.lastOutMs(listOf(a), s, start + 10 * min))
    }

    @Test
    fun movingTheStartKeepsARecordedRemoval() {
        // + tapped at 10:12 for a pouch that went in at 10:00, taken out at 10:30
        val e = LogEntry(pouchTypeId = 1, timestamp = start + 12 * min, strengthMg = 8.0, endTimestamp = start + 30 * min, pouchMinutes = 30)
        val fixed = PouchTiming.withNewStart(e, start, s)
        assertEquals(start + 30 * min, fixed.endTimestamp)
        assertEquals(30 * min, PouchTiming.effectiveDurationMs(fixed, s))
        // moved past its removal: the removal moves along and keeps the duration
        val later = PouchTiming.withNewStart(e, start + 40 * min, s)
        assertEquals(start + 58 * min, later.endTimestamp)
        // a pouch still in keeps no end
        assertEquals(null, PouchTiming.withNewStart(e.copy(endTimestamp = null), start, s).endTimestamp)
    }

    @Test
    fun movingTheStartKeepsTheDayUnlessItLandsInAnother() {
        // Logged at 02:00 while days started at midnight; they start at 04:00 now.
        val at = LocalDateTime.of(2026, 10, 19, 2, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val e = LogEntry(pouchTypeId = 1, timestamp = at, strengthMg = 8.0, pouchMinutes = 30, day = DayKeys.epochDay(at, 0))
        val fourAm = Settings(dayStartHour = 4)
        // five minutes earlier: still the day it was logged into, not yesterday under the new rule
        assertEquals(e.day, PouchTiming.withNewStart(e, at - 5 * min, fourAm).day)
        // to 05:00, past the 04:00 boundary: the day 05:00 belongs to
        assertEquals(DayKeys.epochDay(at + 180 * min, 4), PouchTiming.withNewStart(e, at + 180 * min, fourAm).day)
    }

    @Test
    fun aNewEntryRecordsItsPouchTimeAndDay() {
        val e = PouchTiming.newEntry(7, 6.0, 0.08, start, Settings(pk = PkParams(absorptionMinutes = 40), dayStartHour = 4))
        assertEquals(40, e.pouchMinutes)
        assertEquals(DayKeys.epochDay(start, 4), e.day)
    }

    @Test
    fun rowsWithoutAStoredTimeFollowTheSetting() {
        val e = LogEntry(pouchTypeId = 1, timestamp = start, strengthMg = 8.0)
        assertEquals(20 * min, PouchTiming.effectiveDurationMs(e, Settings(pk = PkParams(absorptionMinutes = 20))))
        assertEquals(30, PouchTiming.newPouchMinutes(s))
    }
}

package app.pouchwise

import app.pouchwise.data.Backup
import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.data.pricePerPouch
import app.pouchwise.model.Brands
import app.pouchwise.model.DayKeys
import app.pouchwise.model.IntervalMode
import app.pouchwise.model.IntervalPlan
import app.pouchwise.model.LimitMode
import app.pouchwise.model.Limits
import app.pouchwise.model.Pharmacokinetics
import app.pouchwise.model.PlanHistory
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import app.pouchwise.model.Stats
import app.pouchwise.util.Export
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Ten days of use, across the night the clocks go back, run through the model, the plan, the exports and the import
 * together, the way the app chains them: pouches logged with the plan level of their moment, some pulled out early or
 * corrected, felt answers given live and from memory, then everything exported and read back.
 */
class ScenarioTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val min = 60_000L
    private val s = Settings(
        limitMode = LimitMode.MG, dailyLimitMg = 49.0, dayStartHour = 4, bodyWeightKg = 72.0, intervalMode = IntervalMode.KEEP,
    )
    private val types = listOf(
        PouchType(id = 1, name = "Loop", strengthMg = 7.1, brand = "Loop", releaseK = Brands.kFor("Loop"), pricePerCan = 6.0, pouchesPerCan = 20),
        PouchType(id = 2, name = "ZYN lemon", strengthMg = 6.5, brand = "ZYN", releaseK = Brands.kFor("ZYN"), pricePerCan = 5.0, pouchesPerCan = 20),
    )
    private val clockTimes = listOf(7 to 10, 9 to 0, 11 to 30, 14 to 0, 16 to 30, 19 to 0, 22 to 0, 25 to 30) // 25:30 = 01:30 next morning
    private val firstDay = LocalDate.of(2026, 10, 20)
    private lateinit var savedZone: TimeZone

    @Before fun zone() { savedZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone(zone)) }
    @After fun restore() { TimeZone.setDefault(savedZone) }

    /** Wall-clock time as the phone shows it; [hour] runs past 24 for the small hours of the next day. */
    private fun at(day: LocalDate, hour: Int, minute: Int): Long =
        day.plusDays(hour / 24L).atTime(hour % 24, minute).atZone(zone).toInstant().toEpochMilli()

    /** Logs pouches the way PouchLog does: the plan level comes from what was logged before. */
    private fun history(days: Int, extra: List<Long> = emptyList()): List<LogEntry> {
        val times = ((0 until days).flatMap { d -> clockTimes.map { (h, m) -> at(firstDay.plusDays(d.toLong()), h, m) } } + extra).sorted()
        val entries = mutableListOf<LogEntry>()
        times.forEachIndexed { i, t ->
            val type = types[i % 2]
            val plan = IntervalPlan.threshold(entries.filter { it.timestamp >= t - IntervalPlan.DATA_MS }, s, t, zone)
            var e = PouchTiming.newEntry(type.id, type.strengthMg, type.releaseK, t, s, type.pricePerPouch(), plan).copy(id = i + 1L)
            if (i % 3 == 0) e = PouchTiming.finish(e, s, t + 18 * min)
            entries += e
        }
        return entries
    }

    @Test
    fun aPouchAfterMidnightBelongsToTheDayBeforeAndEveryDayIsCounted() {
        val entries = history(10)
        val stats = Stats.dailyStats(entries, s, firstDay.toEpochDay(), firstDay.toEpochDay() + 9)
        assertEquals(List(10) { 8 }, stats.map { it.count })
        val lateOne = entries.first { it.timestamp == at(firstDay, 25, 30) }
        assertEquals(firstDay.toEpochDay(), DayKeys.of(lateOne, s.dayStartHour, zone))
        // Every day's first pouch is the 07:10 one, not the 01:30 one that closed the night before.
        val sevenTen = entries.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalTime() == LocalTime.of(7, 10) }
        assertEquals(sevenTen.map { it.id }.toSet(), IntervalPlan.firstOfDayIds(entries, s))
    }

    @Test
    fun theFullDayLeavesNoRoomAndAPouchSoonAfterAnotherIsEarly() {
        val soon = at(firstDay.plusDays(9), 9, 45)
        val entries = history(10, extra = listOf(soon))
        val lastDay = firstDay.plusDays(9).toEpochDay()
        val today = entries.filter { DayKeys.of(it, s.dayStartHour, zone) == lastDay }
        val mg = today.sumOf { it.strengthMg }
        val limit = Limits.limitForDayRounded(s, lastDay)
        assertTrue("9 pouches, $mg mg, are over 49", Limits.left(s.limitMode, limit, today.size, mg) < 0)
        assertTrue(Limits.noneFit(s, limit, today.size, mg, types.map { it.strengthMg }))
        // The plan learnt a usual level from the earlier days, and the pouch 45 minutes after another went in above it.
        val early = IntervalPlan.earlyIds(today, entries, s)
        assertTrue("planned from day two", entries.drop(clockTimes.size).all { it.planLevelMg != null })
        assertTrue(entries.single { it.timestamp == soon }.id in early)
    }

    @Test
    fun everythingExportedComesBackAndTheCsvsAgreeWithTheModel() {
        val entries = history(10).toMutableList()
        // A late tap on + corrected: it went in ten minutes earlier and stayed 25 minutes.
        val fixed = entries[20]
        entries[20] = PouchTiming.withTimes(fixed, fixed.timestamp - 10 * min, 25 * min, s)
        val live = FeltMark(entryId = entries[5].id, level = Felt.HIT.level, atMs = entries[5].timestamp + 4 * min)
        val next = FeltMark(entryId = entries[5].id, level = Felt.OVERWHELM.level, atMs = entries[5].timestamp + 20 * min)
        val fromMemory = PouchTiming.placeAnswer(
            FeltMark(entryId = entries[30].id, level = Felt.NOTHING.level, atMs = entries[30].timestamp + 2 * 24 * 60 * min), entries[30], s,
        )
        val marks = listOf(live, next, fromMemory)
        assertTrue(fromMemory.recalled)
        assertEquals(PouchTiming.outMs(entries[30], s), fromMemory.atMs)

        val plans = listOf(PlanHistory.versionOf(s, fromDay = firstDay.toEpochDay()))
        val back = Backup.parse(Export.json(entries, types, s, "test", plans, marks))
        assertEquals(types, back.types)
        assertEquals(entries, back.entries)
        assertEquals(plans, back.plans)
        assertEquals(marks.sortedBy { it.atMs }, back.feltMarks)
        val restored = back.settingsOver(Settings())
        assertEquals(listOf(s.limitMode, s.dailyLimitMg, s.dayStartHour, s.bodyWeightKg, s.intervalMode),
            listOf(restored.limitMode, restored.dailyLimitMg, restored.dayStartHour, restored.bodyWeightKg, restored.intervalMode))

        val pouchRows = Export.csv(entries, types, s, marks).trim().lines()
        assertEquals(entries.size + 1, pouchRows.size)
        val pouchNames = pouchRows[0].split(',')
        fun pouchCell(e: LogEntry, name: String) = pouchRows[entries.indexOf(e) + 1].split(',')[pouchNames.indexOf(name)]
        assertEquals("25.0", pouchCell(entries[20], "duration_min"))
        assertEquals(firstDay.toString(), pouchCell(entries.first { it.timestamp == at(firstDay, 25, 30) }, "day"))
        val feltRows = Export.feltCsv(entries, types, s, marks).trim().lines()
        val names = feltRows[0].split(',')
        fun cell(row: Int, name: String) = feltRows[row].split(',')[names.indexOf(name)]
        assertEquals(marks.size + 1, feltRows.size)
        assertEquals(listOf("hit", "overwhelm", "nothing"), (1..3).map { cell(it, "felt") })
        assertEquals(listOf("1", "1", "0"), (1..3).map { cell(it, "in_mouth") })
        assertEquals("1", cell(3, "recalled"))
        assertEquals(cell(3, "nicotine_at_pull_out_ng_ml"), cell(3, "nicotine_ng_ml"))
        // The level rises while the pouch is in, and each number is the model's own, to the CSV's one decimal.
        assertTrue(cell(2, "nicotine_ng_ml").toDouble() > cell(1, "nicotine_ng_ml").toDouble())
        val doses = entries.map { PouchTiming.toDose(it, s) }
        val atLive = Pharmacokinetics.levelsAt(live.atMs, doses, s.pk).nicotineMg
        assertEquals(Pharmacokinetics.toNgPerMl(atLive, s.bodyWeightKg, Pharmacokinetics.NICOTINE_VD_L_PER_KG), cell(1, "nicotine_ng_ml").toDouble(), 0.051)
    }

    @Test
    fun theNightTheClocksGoBackIsOneLongTrackingDay() {
        // Europe/Rome leaves summer time at 03:00 on 25 October 2026; with days starting at 04:00 that night belongs to the 24th.
        val day = LocalDate.of(2026, 10, 24).toEpochDay()
        assertEquals(25 * 60 * min, DayKeys.endOfDayMs(day, 4, zone) - DayKeys.startOfDayMs(day, 4, zone))
        val beforeChange = ZonedDateTime.of(LocalDateTime.of(2026, 10, 25, 2, 30), zone).withEarlierOffsetAtOverlap()
        val afterChange = beforeChange.withLaterOffsetAtOverlap()
        val nextMorning = ZonedDateTime.of(LocalDateTime.of(2026, 10, 25, 4, 0), zone)
        assertEquals(60 * min, afterChange.toInstant().toEpochMilli() - beforeChange.toInstant().toEpochMilli())
        assertEquals(day, DayKeys.epochDay(beforeChange.toInstant().toEpochMilli(), 4, zone))
        assertEquals(day, DayKeys.epochDay(afterChange.toInstant().toEpochMilli(), 4, zone))
        assertEquals(day + 1, DayKeys.epochDay(nextMorning.toInstant().toEpochMilli(), 4, zone))
    }
}

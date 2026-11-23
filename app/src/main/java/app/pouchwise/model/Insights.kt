package app.pouchwise.model

import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class SpendingSummary(
    /** Since the Monday of this week. */
    val week: Double,
    /** Since the first of this month. */
    val month: Double,
    /** Average price of a pouch over all priced entries; null without any. */
    val perPouch: Double?,
    /** Saved over the finished days since [sinceDay]; null without a price or a baseline. */
    val saved: Double?,
    val sinceDay: Long?,
    /** Some pouches this week or month had no price, so the sums above leave them out. */
    val unpriced: Boolean,
)

object Spending {
    /**
     * Money spent from the price each pouch was logged at, and the saving against the pouches a day before the app:
     * (that many a day − pouches actually used) × the average price of a pouch, over the finished days.
     */
    fun summary(entries: List<LogEntry>, s: Settings, today: Long): SpendingSummary {
        val byDay = entries.groupBy { DayKeys.of(it, s.dayStartHour) }
        fun spent(days: LongRange) = days.sumOf { d -> byDay[d].orEmpty().sumOf { it.pricePerPouch ?: 0.0 } }
        val date = LocalDate.ofEpochDay(today)
        val weekStart = today - (date.dayOfWeek.value - 1)
        val monthStart = date.withDayOfMonth(1).toEpochDay()
        val perPouch = entries.mapNotNull { it.pricePerPouch }.takeIf { it.isNotEmpty() }?.average()
        val firstDay = byDay.keys.minOrNull()
        val baseline = s.baselinePerDay
        val saved = if (perPouch != null && baseline != null && firstDay != null && firstDay < today) {
            val finished = firstDay until today
            val used = finished.sumOf { byDay[it]?.size ?: 0 }
            (baseline.toLong() * (today - firstDay) - used) * perPouch
        } else null
        return SpendingSummary(
            week = spent(weekStart..today),
            month = spent(monthStart..today),
            perPouch = perPouch,
            saved = saved,
            sinceDay = firstDay,
            unpriced = (min(weekStart, monthStart)..today).any { d -> byDay[d].orEmpty().any { it.pricePerPouch == null } },
        )
    }
}

data class PatternSummary(
    /** Pouches by clock hour, 0 to 23. */
    val byHour: List<Int>,
    /** Average pouches a day by weekday over the finished days, Monday first. */
    val byWeekday: List<Double>,
    /** Minutes after the start of each tracking day at which its first pouch went in, oldest day first. */
    val firstPouch: List<Int>,
    /** Clock time (minutes after midnight) of the first pouch, averaged over the last seven days with pouches. */
    val firstNow: Int?,
    /** How much later (positive) or earlier the first pouch comes now than at the start of the period. */
    val firstShift: Int?,
    /** Median time without a pouch: from one coming out to the next going in on the same day. */
    val typicalBreakMs: Long?,
    /** Average time a pouch stayed in. */
    val avgInMouthMs: Long?,
)

object Patterns {
    const val DAYS = 30
    private const val TREND_DAYS = 7

    fun summary(entries: List<LogEntry>, s: Settings, today: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): PatternSummary {
        val fromDay = today - (DAYS - 1)
        val perDay = entries.groupBy { DayKeys.of(it, s.dayStartHour) }.filterKeys { it in fromDay..today }.toSortedMap()
        val inWindow = perDay.values.flatten()

        val byHour = IntArray(24)
        inWindow.forEach { byHour[Instant.ofEpochMilli(it.timestamp).atZone(zone).hour]++ }

        // Four of each weekday, all finished.
        val firstLogged = entries.minOfOrNull { DayKeys.of(it, s.dayStartHour) } ?: today
        val days = max(today - 4 * 7, firstLogged) until today
        val byWeekday = (1..7).map { dow ->
            val same = days.filter { LocalDate.ofEpochDay(it).dayOfWeek.value == dow }
            if (same.isEmpty()) 0.0 else same.sumOf { perDay[it]?.size ?: 0 }.toDouble() / same.size
        }

        val dayStartMin = s.dayStartHour * 60
        val firstPouch = perDay.values.map { list ->
            val t = Instant.ofEpochMilli(list.minOf { it.timestamp }).atZone(zone)
            Math.floorMod(t.hour * 60 + t.minute - dayStartMin, 24 * 60)
        }
        val recent = firstPouch.takeLast(TREND_DAYS)
        val firstNow = recent.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.let { Math.floorMod(it + dayStartMin, 24 * 60) }
        val spanDays = perDay.keys.let { if (it.isEmpty()) 0L else it.last() - it.first() }
        val firstShift = if (firstPouch.size >= 2 * TREND_DAYS && spanDays >= 2L * TREND_DAYS) {
            (recent.average() - firstPouch.take(TREND_DAYS).average()).roundToInt()
        } else null

        // Two pouches in at once leave no break at all.
        val breaks = perDay.values.flatMap { list ->
            list.sortedBy { it.timestamp }.zipWithNext { a, b -> (b.timestamp - PouchTiming.outMs(a, s)).coerceAtLeast(0L).toDouble() }
        }
        val inMouth = inWindow.filter { !PouchTiming.isActive(it, s, nowMs) && it.timestamp <= nowMs }
            .map { PouchTiming.effectiveDurationMs(it, s) }
        return PatternSummary(
            byHour = byHour.toList(),
            byWeekday = byWeekday,
            firstPouch = firstPouch,
            firstNow = firstNow,
            firstShift = firstShift,
            typicalBreakMs = Stats.median(breaks)?.roundToLong(),
            avgInMouthMs = inMouth.takeIf { it.isNotEmpty() }?.average()?.roundToLong(),
        )
    }
}

/** One Monday-to-Sunday week: answered pouches by their strongest answer, nothing first. */
data class FeltWeek(val monday: Long, val fromDay: Long, val counts: List<Int>)

data class FeltSummary(
    /** Oldest first; the first may start inside its week, where the period does. */
    val weeks: List<FeltWeek>,
    /** Each day's first pouch, counted the same way. */
    val firstOfDay: List<Int>,
    val answered: Int,
    val pouches: Int,
)

object FeltPatterns {
    /**
     * How pouches felt over the last [Patterns.DAYS] days. Pouches in the mouth together count once, as [SharedFelt.groups],
     * by the strongest answer of any of them.
     */
    fun summary(entries: List<LogEntry>, marks: Map<Long, List<FeltMark>>, s: Settings, today: Long): FeltSummary {
        val fromDay = today - (Patterns.DAYS - 1)
        val inWindow = entries.filter { DayKeys.of(it, s.dayStartHour) in fromDay..today }
        val groups = SharedFelt.groups(inWindow, s)
        val strongest = groups.mapNotNull { g -> g.mapNotNull { e -> marks[e.id]?.maxOfOrNull { it.level } }.maxOrNull()?.let { g to it } }
        fun counts(levels: List<Int>) = Felt.entries.map { f -> levels.count { it == f.level } }
        val weeks = strongest
            .groupBy { (g, _) -> DayKeys.of(g.first(), s.dayStartHour).let { it - (LocalDate.ofEpochDay(it).dayOfWeek.value - 1) } }
            .toSortedMap()
            .map { (monday, list) -> FeltWeek(monday, max(monday, fromDay), counts(list.map { it.second })) }
        val first = IntervalPlan.firstOfDayIds(inWindow, s)
        return FeltSummary(weeks, counts(strongest.filter { (g, _) -> g.any { it.id in first } }.map { it.second }), strongest.size, groups.size)
    }
}

/** Receptor rest on one tracking day; a [running] day counts only up to now. */
data class RestDay(val epochDay: Long, val restMs: Long, val running: Boolean)

/** Average rest a day over the finished days of the Monday-to-Sunday week from [monday]. */
data class RestWeek(val monday: Long, val avgMs: Long)

/** The Body screen's "Receptors at rest today", day by day. */
object ReceptorRest {
    /** From the day after the first logged one, whose night before is unknown, through [today]. */
    fun days(entries: List<LogEntry>, s: Settings, today: Long): LongRange =
        entries.minOfOrNull { DayKeys.of(it, s.dayStartHour) }?.let { it + 1..today } ?: LongRange.EMPTY

    /** Each of [days] from its start to the next day's start or [nowMs], whichever comes first. */
    fun byDay(doses: List<Dose>, s: Settings, days: LongRange, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<RestDay> {
        val level = IntervalPlan.restLevelMg(s)
        return days.map { day ->
            val start = DayKeys.startOfDayMs(day, s.dayStartHour, zone)
            val end = DayKeys.endOfDayMs(day, s.dayStartHour, zone)
            RestDay(day, IntervalPlan.restMs(doses, s.pk, level, start, min(end, nowMs)), running = end > nowMs)
        }
    }

    /** Oldest first. */
    fun weeks(days: List<RestDay>): List<RestWeek> =
        days.filterNot { it.running }
            .groupBy { it.epochDay - (LocalDate.ofEpochDay(it.epochDay).dayOfWeek.value - 1) }
            .toSortedMap()
            .map { (monday, own) -> RestWeek(monday, own.sumOf { it.restMs } / own.size) }
}

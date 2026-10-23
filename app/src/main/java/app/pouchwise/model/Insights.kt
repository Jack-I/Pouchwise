package app.pouchwise.model

import app.pouchwise.data.LogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class SpendingSummary(
    val today: Double,
    /** Since the Monday of this week. */
    val week: Double,
    /** Since the first of this month. */
    val month: Double,
    /** Average price of a pouch over all priced entries; null without any. */
    val perPouch: Double?,
    /** Saved over the finished days since [sinceDay]; null without a price or a baseline. */
    val saved: Double?,
    val sinceDay: Long?,
    /** Some pouches this month had no price, so the sums above leave them out. */
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
            today = spent(today..today),
            week = spent(weekStart..today),
            month = spent(monthStart..today),
            perPouch = perPouch,
            saved = saved,
            sinceDay = firstDay,
            unpriced = (monthStart..today).any { d -> byDay[d].orEmpty().any { it.pricePerPouch == null } },
        )
    }
}

data class PatternSummary(
    /** Pouches by clock hour, 0 to 23. */
    val byHour: List<Int>,
    /** Average pouches a day by weekday, Monday first. */
    val byWeekday: List<Double>,
    /** Minutes after the start of each tracking day at which its first pouch went in, oldest day first. */
    val firstPouch: List<Int>,
    /** Clock time (minutes after midnight) of the first pouch, averaged over the last seven days with pouches. */
    val firstNow: Int?,
    /** How much later (positive) or earlier the first pouch comes now than at the start of the period. */
    val firstShift: Int?,
    /** Median time between one pouch and the next on the same day. */
    val typicalGapMs: Long?,
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

        val firstLogged = entries.minOfOrNull { DayKeys.of(it, s.dayStartHour) } ?: today
        val days = max(fromDay, firstLogged)..today
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

        val gaps = perDay.values.flatMap { list -> list.sortedBy { it.timestamp }.zipWithNext { a, b -> (b.timestamp - a.timestamp).toDouble() } }
        val inMouth = inWindow.filter { !PouchTiming.isActive(it, s, nowMs) && it.timestamp <= nowMs }
            .map { PouchTiming.effectiveDurationMs(it, s) }
        return PatternSummary(
            byHour = byHour.toList(),
            byWeekday = byWeekday,
            firstPouch = firstPouch,
            firstNow = firstNow,
            firstShift = firstShift,
            typicalGapMs = Stats.median(gaps)?.roundToLong(),
            avgInMouthMs = inMouth.takeIf { it.isNotEmpty() }?.average()?.roundToLong(),
        )
    }
}

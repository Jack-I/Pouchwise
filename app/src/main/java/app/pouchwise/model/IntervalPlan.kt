package app.pouchwise.model

import app.pouchwise.data.LogEntry
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Spacing between pouches by nicotine level. "Keep": the next pouch once nicotine is back below the level at
 * which you usually take one (median over the last two weeks). "Lower": 15 % below that, fixed for the week
 * from the two weeks before Monday, and never below the level at which half of the α4β2 receptors are free.
 */
object IntervalPlan {
    /** Plasma nicotine at which half of the brain's α4β2 nicotinic receptors are taken (Brody et al., Arch Gen Psychiatry 2006). */
    const val RECEPTOR_HALF_NG_ML = 0.87
    const val LOWER_SHARE = 0.85
    const val WINDOW_MS = 14L * 24 * 3_600_000L
    /** Entries a plan needs loaded: the week so far, two weeks before it, and the model's lookback. */
    const val DATA_MS = 28L * 24 * 3_600_000L
    private const val MIN_SAMPLES = 5

    /** Nicotine in the body (mg) at which the receptors are half free, for this body weight. */
    fun restLevelMg(s: Settings): Double =
        Pharmacokinetics.toMg(RECEPTOR_HALF_NG_ML, s.bodyWeightKg, Pharmacokinetics.NICOTINE_VD_L_PER_KG)

    /** Ids of the first pouch of each tracking day: after the night it starts near zero and says nothing about spacing. */
    fun firstOfDayIds(entries: List<LogEntry>, s: Settings): Set<Long> =
        entries.groupBy { DayKeys.of(it, s.dayStartHour) }.values.map { day -> day.minBy { it.timestamp }.id }.toSet()

    /** Nicotine (mg) the model had in the body when each pouch from [fromMs] to [toMs] went in, the day's first left out. */
    fun levelsAtPouches(entries: List<LogEntry>, s: Settings, fromMs: Long, toMs: Long): List<Double> {
        val doses = entries.map { PouchTiming.toDose(it, s) }
        val first = firstOfDayIds(entries, s)
        return entries
            .filter { it.timestamp in fromMs until toMs && it.id !in first }
            .map { Pharmacokinetics.levelsAt(it.timestamp, doses, s.pk).nicotineMg }
    }

    /** Median of [levelsAtPouches], or null with fewer than five pouches to go by. */
    fun usualLevel(entries: List<LogEntry>, s: Settings, fromMs: Long, toMs: Long): Double? {
        val levels = levelsAtPouches(entries, s, fromMs, toMs).sorted()
        if (levels.size < MIN_SAMPLES) return null
        val mid = levels.size / 2
        return if (levels.size % 2 == 1) levels[mid] else (levels[mid - 1] + levels[mid]) / 2
    }

    /** Level (mg) below which the next pouch is due at [nowMs]; null with the plan off or too little data yet. */
    fun threshold(entries: List<LogEntry>, s: Settings, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Double? =
        when (s.intervalMode) {
            IntervalMode.OFF -> null
            IntervalMode.KEEP -> usualLevel(entries, s, nowMs - WINDOW_MS, nowMs)
            IntervalMode.LOWER -> {
                val monday = weekStartMs(nowMs, s.dayStartHour, zone)
                // In the first weeks there is nothing before Monday yet; the last two weeks stand in.
                val usual = usualLevel(entries, s, monday - WINDOW_MS, monday) ?: usualLevel(entries, s, nowMs - WINDOW_MS, nowMs)
                usual?.let { max(it * LOWER_SHARE, restLevelMg(s)) }
            }
        }

    /** Start of the tracking day that is the Monday of [nowMs]'s week. */
    fun weekStartMs(nowMs: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = DayKeys.epochDay(nowMs, dayStartHour, zone)
        val monday = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
        return DayKeys.startOfDayMs(monday, dayStartHour, zone)
    }

    /** When nicotine, with no more pouches, drops below [levelMg] for good; null when it already is and stays there. */
    fun nextOnPlanMs(doses: List<Dose>, p: PkParams, levelMg: Double, nowMs: Long): Long? =
        Pharmacokinetics.clearance(
            nowMs, doses, p,
            nicotineThreshold = levelMg,
            cotinineThreshold = Double.POSITIVE_INFINITY,
            stepMs = 60_000L,
        ).nicotineClearMs

    /** Pouches that went in while nicotine was still at or above their plan level; the day's first never counts. */
    fun earlyIds(dayEntries: List<LogEntry>, allEntries: List<LogEntry>, s: Settings): Set<Long> {
        if (dayEntries.none { it.planLevelMg != null }) return emptySet()
        val doses = allEntries.map { PouchTiming.toDose(it, s) }
        val first = dayEntries.minByOrNull { it.timestamp }?.id
        return dayEntries
            .filter { e -> e.id != first && e.planLevelMg != null && Pharmacokinetics.levelsAt(e.timestamp, doses, s.pk).nicotineMg >= e.planLevelMg }
            .map { it.id }
            .toSet()
    }

    /** Time from [fromMs] to [toMs] with nicotine below [restLevelMg], when more than half of the receptors are free. */
    fun restMs(doses: List<Dose>, p: PkParams, restLevelMg: Double, fromMs: Long, toMs: Long, stepMs: Long = 5 * 60_000L): Long {
        val recent = doses.filter { it.timeMs <= toMs && toMs - it.timeMs <= Pharmacokinetics.LOOKBACK_MS + (toMs - fromMs) }
        var rest = 0L
        var t = fromMs
        while (t < toMs) {
            val step = min(stepMs, toMs - t)
            if (Pharmacokinetics.levelsAt(t + step / 2, recent, p).nicotineMg < restLevelMg) rest += step
            t += step
        }
        return rest
    }
}

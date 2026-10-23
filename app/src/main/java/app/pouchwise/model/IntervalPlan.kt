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
    private const val MINUTE_MS = 60_000L

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
        val levels = levelsAtPouches(entries, s, fromMs, toMs)
        return if (levels.size < MIN_SAMPLES) null else Stats.median(levels)
    }

    /**
     * Level (mg) below which the next pouch is due at [nowMs]; null with the plan off or too little data yet. It is fixed
     * for the whole tracking day (the two weeks before it), so the time shown and the level a pouch records agree.
     */
    fun threshold(entries: List<LogEntry>, s: Settings, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Double? {
        if (s.intervalMode == IntervalMode.OFF) return null
        val recent = entries.filter { it.timestamp in nowMs - DATA_MS..nowMs }
        val dayStart = DayKeys.startOfDayMs(DayKeys.epochDay(nowMs, s.dayStartHour, zone), s.dayStartHour, zone)
        val usual = { end: Long -> usualLevel(recent, s, end - WINDOW_MS, end) }
        return when (s.intervalMode) {
            IntervalMode.OFF -> null
            IntervalMode.KEEP -> usual(dayStart)
            // In the first weeks there is nothing before Monday yet; the two weeks before today stand in.
            IntervalMode.LOWER -> (usual(weekStartMs(nowMs, s.dayStartHour, zone)) ?: usual(dayStart))?.let { max(it * LOWER_SHARE, restLevelMg(s)) }
        }
    }

    /** Start of the tracking day that is the Monday of [nowMs]'s week. */
    fun weekStartMs(nowMs: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = DayKeys.epochDay(nowMs, dayStartHour, zone)
        val monday = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
        return DayKeys.startOfDayMs(monday, dayStartHour, zone)
    }

    /**
     * When nicotine, with no more pouches, drops below [levelMg] for good: a whole minute, so a pouch put in at the
     * minute shown is not early. Null when it already is below, and stays there.
     */
    fun nextOnPlanMs(doses: List<Dose>, p: PkParams, levelMg: Double, nowMs: Long): Long? =
        Pharmacokinetics.clearance(
            Math.floorDiv(nowMs, MINUTE_MS) * MINUTE_MS, doses, p,
            nicotineThreshold = levelMg,
            cotinineThreshold = Double.POSITIVE_INFINITY,
            stepMs = MINUTE_MS,
        ).nicotineClearMs?.takeIf { it > nowMs }

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

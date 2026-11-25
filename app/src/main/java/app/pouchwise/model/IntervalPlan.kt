package app.pouchwise.model

import app.pouchwise.data.LogEntry
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Spacing between pouches by nicotine level. "Keep": the next pouch once nicotine is back below the level at
 * which you usually take one (median over the last two weeks). "Lower": 17 % below that, fixed for the week
 * from the two weeks before Monday, and never below the level at which half of the α4β2 receptors are free.
 * Neither goes above the level just before each next pouch when a typical pouch of yours comes every two hours: a
 * habit of back-to-back pouches is not what the plan holds. In both, the next one also waits [MIN_BREAK_MS] after the last.
 */
object IntervalPlan {
    /** Plasma nicotine at which half of the brain's α4β2 nicotinic receptors are taken (Brody et al., Arch Gen Psychiatry 2006). */
    const val RECEPTOR_HALF_NG_ML = 0.87
    const val LOWER_SHARE = 0.83
    const val WINDOW_MS = 14L * 24 * 3_600_000L
    /** Entries a plan needs loaded: the week so far, two weeks before it, and the model's lookback. */
    const val DATA_MS = 28L * 24 * 3_600_000L
    /** The cap allows a typical pouch this often. */
    const val CAP_EVERY_MS = 2 * 3_600_000L
    /** However low nicotine is, the next pouch is better no sooner than this after the last one comes out. */
    const val MIN_BREAK_MS = 3_600_000L
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

    /** Nicotine (mg) [pouch] alone has left [afterMs] after it went in, with its own strength, release and time in. */
    fun leftAfterMg(pouch: LogEntry, s: Settings, afterMs: Long = CAP_EVERY_MS): Double =
        Pharmacokinetics.levelsAt(pouch.timestamp + afterMs, listOf(PouchTiming.toDose(pouch, s)), s.pk).nicotineMg

    /**
     * How much more there is before each next pouch when the same one comes every [everyMs] for good than after one alone:
     * what is left of every earlier pouch adds up, by the nicotine half-life.
     */
    fun buildUp(everyMs: Long, s: Settings): Double =
        1 / (1 - Math.pow(0.5, everyMs / (s.pk.nicotineHalfLifeHours * 3_600_000.0)))

    /** What the day of [nowMs] goes by, from the two weeks before it: the usual level, the cap and the pouch it stands for. */
    class Basis(val usualMg: Double?, val capMg: Double?, val typical: LogEntry?)

    /**
     * The cap is the level before each next pouch when a typical one comes every [CAP_EVERY_MS]: the median over every pouch of
     * those two weeks of what it leaves by then, so no one kind sets it, built up as by [buildUp].
     */
    fun basis(entries: List<LogEntry>, s: Settings, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Basis {
        val recent = entries.filter { it.timestamp in nowMs - DATA_MS..nowMs }
        val dayStart = DayKeys.startOfDayMs(DayKeys.epochDay(nowMs, s.dayStartHour, zone), s.dayStartHour, zone)
        val left = recent.filter { it.timestamp in dayStart - WINDOW_MS until dayStart }.map { it to leftAfterMg(it, s) }
        val median = Stats.median(left.map { it.second })
        val typical = median?.let { m -> left.minBy { abs(it.second - m) }.first }
        return Basis(usualLevel(recent, s, dayStart - WINDOW_MS, dayStart), median?.let { it * buildUp(CAP_EVERY_MS, s) }, typical)
    }

    /**
     * How often [pouch] can come again and again with nicotine just before each next one at or below [levelMg]: the shortest
     * such interval in five-minute steps, from the pouch's own time in the mouth and [MIN_BREAK_MS] up to half a day; null if
     * not even then.
     */
    fun repeatEveryMs(pouch: LogEntry, levelMg: Double, s: Settings): Long? {
        val step = 5 * MINUTE_MS
        var every = (PouchTiming.effectiveDurationMs(pouch, s) + MIN_BREAK_MS + step - 1) / step * step
        while (every <= 12 * 3_600_000L) {
            if (leftAfterMg(pouch, s, every) * buildUp(every, s) <= levelMg) return every
            every += step
        }
        return null
    }

    /**
     * Level (mg) below which the next pouch is due at [nowMs]; null with the plan off or too little data yet. It is fixed
     * for the whole tracking day (the two weeks before it), so the time shown and the level a pouch records agree.
     */
    fun threshold(entries: List<LogEntry>, s: Settings, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Double? {
        if (s.intervalMode == IntervalMode.OFF) return null
        val day = basis(entries, s, nowMs, zone)
        fun capped(level: Double) = day.capMg?.let { min(level, it) } ?: level
        val keep = capped(day.usualMg ?: return null)
        return when (s.intervalMode) {
            IntervalMode.OFF -> null
            IntervalMode.KEEP -> keep
            IntervalMode.LOWER -> {
                // Two full weeks before Monday set the week's level; until there are, the two weeks before today stand in.
                val weekStart = weekStartMs(nowMs, s.dayStartHour, zone)
                val recent = entries.filter { it.timestamp in nowMs - DATA_MS..nowMs }
                val week = if (recent.none { it.timestamp < weekStart - WINDOW_MS }) null else usualLevel(recent, s, weekStart - WINDOW_MS, weekStart)
                max((week?.let { capped(it) } ?: keep) * LOWER_SHARE, restLevelMg(s))
            }
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
        ).nicotineClearMs

    /** When the next pouch is better, and whether the break after the last one, not the level, sets that time. */
    data class Next(val atMs: Long, val byBreak: Boolean)

    /**
     * When [MIN_BREAK_MS] after the last pouch out is over, on the next whole minute: from its own time up while it is still
     * in, from the last one out of a double-decker. Null when it already is.
     */
    fun breakOverMs(doses: List<Dose>, nowMs: Long): Long? {
        val lastOut = doses.filter { it.timeMs <= nowMs }.maxOfOrNull { it.timeMs + it.durationMs } ?: return null
        return (Math.floorDiv(lastOut + MIN_BREAK_MS + MINUTE_MS - 1, MINUTE_MS) * MINUTE_MS).takeIf { it > nowMs }
    }

    /** The later of [nextOnPlanMs] and [breakOverMs]; null when neither is still ahead. */
    fun next(doses: List<Dose>, p: PkParams, levelMg: Double, nowMs: Long): Next? {
        val byLevel = nextOnPlanMs(doses, p, levelMg, nowMs)
        val byBreak = breakOverMs(doses, nowMs)
        return when {
            byBreak != null && (byLevel == null || byBreak > byLevel) -> Next(byBreak, byBreak = true)
            byLevel != null -> Next(byLevel, byBreak = false)
            else -> null
        }
    }

    /**
     * Pouches that went in while nicotine was still at or above their plan level, or with none in the mouth before
     * [breakOverMs]; one joining a pouch still in is a double-decker, not a short break. The day's first never counts.
     */
    fun earlyIds(dayEntries: List<LogEntry>, allEntries: List<LogEntry>, s: Settings): Set<Long> {
        if (dayEntries.none { it.planLevelMg != null }) return emptySet()
        val doses = allEntries.map { PouchTiming.toDose(it, s) }
        // A double-decker logged afterwards has one start for both pouches: ties go by id.
        val order = compareBy<LogEntry>({ it.timestamp }, { it.id })
        val first = dayEntries.minWithOrNull(order)?.id
        fun breakTooShort(e: LogEntry): Boolean {
            val before = allEntries.zip(doses).filter { order.compare(it.first, e) < 0 }.map { it.second }
            return before.none { it.timeMs + it.durationMs > e.timestamp } && breakOverMs(before, e.timestamp) != null
        }
        return dayEntries
            .filter { e ->
                e.id != first && e.planLevelMg != null &&
                    (Pharmacokinetics.levelsAt(e.timestamp, doses, s.pk).nicotineMg >= e.planLevelMg || breakTooShort(e))
            }
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

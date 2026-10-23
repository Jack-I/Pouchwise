package app.pouchwise.model

import app.pouchwise.data.LogEntry
import kotlin.math.roundToInt

object Limits {
    /** Limit for a given tracking day, in the unit of the current [LimitMode]. */
    fun limitForDay(s: Settings, epochDay: Long): Double {
        val base = if (s.limitMode == LimitMode.COUNT) s.dailyLimitCount.toDouble() else s.dailyLimitMg
        if (!s.taperEnabled) return base
        if (s.taperTargetDay <= s.taperStartDay) return s.taperTargetValue
        if (epochDay <= s.taperStartDay) return s.taperStartValue
        if (epochDay >= s.taperTargetDay) return s.taperTargetValue
        val frac = (epochDay - s.taperStartDay).toDouble() / (s.taperTargetDay - s.taperStartDay).toDouble()
        return s.taperStartValue + (s.taperTargetValue - s.taperStartValue) * frac
    }

    /** Limits are shown and enforced as whole pouches / whole mg. */
    fun limitForDayRounded(s: Settings, epochDay: Long): Int = limitForDay(s, epochDay).roundToInt()

    /** Usage of a day in the unit of the current mode. */
    fun usage(mode: LimitMode, count: Int, mg: Double): Double =
        if (mode == LimitMode.COUNT) count.toDouble() else mg

    /** True when adding one pouch of [addMg] would exceed the limit. Reaching it exactly is allowed. */
    fun wouldExceed(s: Settings, limit: Int, countSoFar: Int, mgSoFar: Double, addMg: Double): Boolean =
        when (s.limitMode) {
            LimitMode.COUNT -> countSoFar + 1 > limit
            LimitMode.MG -> mgSoFar + addMg > limit + 1e-9
        }

    /** How many more pouches of [strengthMg] fit under the limit today (never negative). */
    fun remainingPouches(s: Settings, limit: Int, countSoFar: Int, mgSoFar: Double, strengthMg: Double): Int =
        when (s.limitMode) {
            LimitMode.COUNT -> (limit - countSoFar).coerceAtLeast(0)
            LimitMode.MG -> if (strengthMg <= 0) 0 else ((limit - mgSoFar + 1e-9) / strengthMg).toInt().coerceAtLeast(0)
        }

    /** True when not one of the pouches of [strengthsMg] fits under the limit today; false with no pouches at all. */
    fun noneFit(s: Settings, limit: Int, countSoFar: Int, mgSoFar: Double, strengthsMg: List<Double>): Boolean =
        strengthsMg.isNotEmpty() && strengthsMg.all { remainingPouches(s, limit, countSoFar, mgSoFar, it) == 0 }

    /**
     * Switches the limit unit, converting the fixed limit and the taper values with a typical pouch
     * strength so that a plan survives the switch instead of being reset. Values left alone since the last
     * switch come back as they were, so switching there and back does not drift (15 mg → 2 pouches → 15 mg).
     */
    fun convertMode(s: Settings, newMode: LimitMode, typicalStrengthMg: Double): Settings {
        if (newMode == s.limitMode) return s
        val k = typicalStrengthMg.coerceAtLeast(0.5)
        val toMg = newMode == LimitMode.MG
        fun toNew(v: Double) = Math.round(if (toMg) v * k else v / k).toDouble()
        fun toOld(v: Double) = Math.round(if (toMg) v / k else v * k).toDouble()
        // The earlier value in the new unit, if the current one is still exactly what converting it gave.
        fun back(current: Double, earlier: Double?) = earlier?.takeIf { toOld(it) == current } ?: toNew(current)
        val switched = s.copy(
            limitMode = newMode,
            taperStartValue = back(s.taperStartValue, s.taperStartAlt),
            taperTargetValue = back(s.taperTargetValue, s.taperTargetAlt),
            taperStartAlt = s.taperStartValue,
            taperTargetAlt = s.taperTargetValue,
        )
        return if (toMg) switched.copy(dailyLimitMg = back(s.dailyLimitCount.toDouble(), s.dailyLimitMg))
               else switched.copy(dailyLimitCount = back(s.dailyLimitMg, s.dailyLimitCount.toDouble()).toInt())
    }

    /** Ids of the entries of one day (in chronological order) that lie beyond the limit. */
    fun overLimitEntryIds(dayEntriesAsc: List<LogEntry>, s: Settings, limit: Int): Set<Long> {
        val over = HashSet<Long>()
        var count = 0
        var mg = 0.0
        for (e in dayEntriesAsc) {
            count++
            mg += e.strengthMg
            val isOver = when (s.limitMode) {
                LimitMode.COUNT -> count > limit
                LimitMode.MG -> mg > limit + 1e-9
            }
            if (isOver) over.add(e.id)
        }
        return over
    }
}

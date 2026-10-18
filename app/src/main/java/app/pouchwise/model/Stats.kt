package app.pouchwise.model

import app.pouchwise.data.LogEntry

data class DayStat(
    val epochDay: Long,
    val count: Int,
    val mg: Double,
    val limit: Int,
    val mode: LimitMode,
    /** False for days before the first log entry (nothing to judge). */
    val hasData: Boolean,
) {
    val used: Double get() = Limits.usage(mode, count, mg)
    val ratio: Double get() = if (limit <= 0) (if (used > 0) 2.0 else 0.0) else used / limit
    val withinLimit: Boolean get() = used <= limit + 1e-9
}

/** Face for a day: three steps within the limit, three beyond it, sunglasses for a pouch-free day. */
enum class GardenLevel(val emoji: String, val label: String) {
    EMPTY("", ""),
    CLEAN("😎", "0"),
    GOOD("😊", "≤33%"),
    OK("🙂", "≤66%"),
    AT_LIMIT("😐", "≤100%"),
    OVER("😬", "≤125%"),
    SAD("😞", "≤150%"),
    FAR_OVER("🤢", ">150%");

    companion object {
        fun of(stat: DayStat?): GardenLevel {
            if (stat == null || !stat.hasData) return EMPTY
            if (stat.count == 0) return CLEAN
            val r = stat.ratio
            return when {
                r <= 1.0 / 3 + 1e-9 -> GOOD
                r <= 2.0 / 3 + 1e-9 -> OK
                r <= 1.0 + 1e-9 -> AT_LIMIT
                r <= 1.25 + 1e-9 -> OVER
                r <= 1.5 + 1e-9 -> SAD
                else -> FAR_OVER
            }
        }
    }
}

object Stats {
    /** Per-day aggregates for [fromDay]..[toDay] inclusive. */
    fun dailyStats(entries: List<LogEntry>, s: Settings, fromDay: Long, toDay: Long): List<DayStat> {
        val counts = HashMap<Long, Int>()
        val mgs = HashMap<Long, Double>()
        var firstDay = Long.MAX_VALUE
        for (e in entries) {
            val d = DayKeys.epochDay(e.timestamp, s.dayStartHour)
            if (d < firstDay) firstDay = d
            counts[d] = (counts[d] ?: 0) + 1
            mgs[d] = (mgs[d] ?: 0.0) + e.strengthMg
        }
        return (fromDay..toDay).map { d ->
            DayStat(
                epochDay = d,
                count = counts[d] ?: 0,
                mg = mgs[d] ?: 0.0,
                limit = Limits.limitForDayRounded(s, d),
                mode = s.limitMode,
                hasData = d >= firstDay,
            )
        }
    }

    /** Consecutive days within the limit, counted backwards from the last element. */
    fun streak(stats: List<DayStat>): Int {
        var n = 0
        for (st in stats.asReversed()) {
            if (!st.hasData) break
            if (st.withinLimit) n++ else break
        }
        return n
    }
}

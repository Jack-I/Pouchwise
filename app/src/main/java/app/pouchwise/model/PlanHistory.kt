package app.pouchwise.model

import app.pouchwise.data.PlanVersion

/**
 * Which limit plan applied on which day. The settings hold the plan for today and the future; each
 * change is recorded from the day it was made, so past days keep the verdict they had.
 */
object PlanHistory {
    fun versionOf(s: Settings, fromDay: Long): PlanVersion = PlanVersion(
        fromDay = fromDay,
        limitMode = s.limitMode.name,
        dailyLimitCount = s.dailyLimitCount,
        dailyLimitMg = s.dailyLimitMg,
        taperEnabled = s.taperEnabled,
        taperStartDay = s.taperStartDay,
        taperStartValue = s.taperStartValue,
        taperTargetDay = s.taperTargetDay,
        taperTargetValue = s.taperTargetValue,
    )

    fun samePlan(a: Settings, b: Settings): Boolean =
        versionOf(a, 0) == versionOf(b, 0)

    /**
     * [current] with the plan of [day]: the latest version that started on or before it, or the oldest one
     * for days before any. Today and later always use [current]. [versions] must be sorted by [PlanVersion.fromDay].
     */
    fun settingsForDay(day: Long, today: Long, versions: List<PlanVersion>, current: Settings): Settings {
        if (day >= today || versions.isEmpty()) return current
        val v = versions.lastOrNull { it.fromDay <= day } ?: versions.first()
        return current.copy(
            limitMode = LimitMode.entries.firstOrNull { it.name == v.limitMode } ?: current.limitMode,
            dailyLimitCount = v.dailyLimitCount,
            dailyLimitMg = v.dailyLimitMg,
            taperEnabled = v.taperEnabled,
            taperStartDay = v.taperStartDay,
            taperStartValue = v.taperStartValue,
            taperTargetDay = v.taperTargetDay,
            taperTargetValue = v.taperTargetValue,
        )
    }
}

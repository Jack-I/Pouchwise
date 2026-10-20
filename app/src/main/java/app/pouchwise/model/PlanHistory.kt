package app.pouchwise.model

import app.pouchwise.data.PlanVersion
import app.pouchwise.data.PouchType
import kotlin.math.roundToInt

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
     * Pouches a day before the app, read back from the first plan: the limit it started from, divided by the
     * strength of the first pouch when it counted mg. Null when there is nothing to go by.
     */
    fun baselinePerDay(first: PlanVersion?, firstType: PouchType?): Int? {
        if (first == null) return null
        val count = first.limitMode == LimitMode.COUNT.name
        val start = when {
            first.taperEnabled -> first.taperStartValue
            count -> first.dailyLimitCount.toDouble()
            else -> first.dailyLimitMg
        }
        val pouches = if (count) start else start / (firstType?.strengthMg?.takeIf { it > 0 } ?: return null)
        return pouches.roundToInt().takeIf { it > 0 }
    }

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

package app.pouchwise.model

import app.pouchwise.data.LogEntry

/**
 * How long a pouch was (or is) in. A pouch counts as removed after the default pouch time
 * ([PkParams.absorptionMinutes]) unless the user removed it earlier.
 */
object PouchTiming {
    private const val MIN_DURATION_MS = 60_000L

    fun autoMs(s: Settings): Long = s.pk.absorptionMinutes.coerceAtLeast(1) * 60_000L

    /** True while the pouch is still in and the automatic removal time has not passed. */
    fun isActive(e: LogEntry, s: Settings, nowMs: Long): Boolean =
        e.endTimestamp == null && nowMs >= e.timestamp && nowMs < e.timestamp + autoMs(s)

    /** Planned or actual time in the mouth, clamped to [1 min, default pouch time]. */
    fun effectiveDurationMs(e: LogEntry, s: Settings): Long {
        val auto = autoMs(s)
        val end = e.endTimestamp ?: return auto
        return (end - e.timestamp).coerceIn(MIN_DURATION_MS, auto)
    }

    fun toDose(e: LogEntry, s: Settings): Dose =
        Dose(e.timestamp, e.strengthMg, effectiveDurationMs(e, s), ReleaseSpeed.fromInt(e.releaseSpeed).kPerMin)

    /** Marks the pouch as taken out now. */
    fun finish(e: LogEntry, s: Settings, nowMs: Long): LogEntry =
        e.copy(endTimestamp = nowMs.coerceIn(e.timestamp + MIN_DURATION_MS, e.timestamp + autoMs(s)))
}

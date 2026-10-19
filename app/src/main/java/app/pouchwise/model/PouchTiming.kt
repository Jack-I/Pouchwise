package app.pouchwise.model

import app.pouchwise.data.LogEntry
import kotlin.math.max
import kotlin.math.min

/**
 * How long a pouch was (or is) in, and how much nicotine it has delivered. A pouch counts as removed
 * after its own pouch time ([LogEntry.pouchMinutes], taken from the settings when it went in) unless
 * the user removed it earlier. Changing the setting later affects new pouches only.
 */
object PouchTiming {
    private const val MIN_DURATION_MS = 60_000L

    /** Upper bound of the pouch-time setting; no pouch can stay in longer. */
    const val MAX_POUCH_MINUTES = 90

    /** Pouch time for a pouch put in now. */
    fun newPouchMinutes(s: Settings): Int = s.pk.absorptionMinutes.coerceAtLeast(1)

    /** A pouch going in at [atMs], with everything that shapes its past fixed now. */
    fun newEntry(pouchTypeId: Long, strengthMg: Double, releaseK: Double, atMs: Long, s: Settings): LogEntry =
        LogEntry(
            pouchTypeId = pouchTypeId,
            timestamp = atMs,
            strengthMg = strengthMg,
            releaseK = releaseK,
            pouchMinutes = newPouchMinutes(s),
            day = DayKeys.epochDay(atMs, s.dayStartHour),
        )

    /** This pouch's full time; rows from before it was stored fall back to the current setting. */
    fun pouchMinutes(e: LogEntry, s: Settings): Int = (e.pouchMinutes ?: s.pk.absorptionMinutes).coerceAtLeast(1)

    fun autoMs(e: LogEntry, s: Settings): Long = pouchMinutes(e, s) * 60_000L

    /** True while the pouch is still in and its automatic removal time has not passed. */
    fun isActive(e: LogEntry, s: Settings, nowMs: Long): Boolean =
        e.endTimestamp == null && nowMs >= e.timestamp && nowMs < e.timestamp + autoMs(e, s)

    /** Planned or actual time in the mouth, clamped to [1 min, the pouch's own time]. */
    fun effectiveDurationMs(e: LogEntry, s: Settings): Long {
        val auto = autoMs(e, s)
        val end = e.endTimestamp ?: return auto
        return (end - e.timestamp).coerceIn(MIN_DURATION_MS, auto)
    }

    fun toDose(e: LogEntry, s: Settings): Dose =
        Dose(e.timestamp, e.strengthMg, effectiveDurationMs(e, s), e.releaseK, pouchMinutes(e, s))

    /**
     * Nicotine absorbed from this pouch by [nowMs]: labelled strength × absorbed share × released
     * share for the minutes it has been in so far (capped at its planned time).
     */
    fun absorbedMg(e: LogEntry, s: Settings, nowMs: Long): Double {
        val planned = effectiveDurationMs(e, s)
        val soFar = min(planned, max(nowMs - e.timestamp, 0L))
        if (soFar <= 0L) return 0.0
        return e.strengthMg * s.pk.absorptionFraction * Pharmacokinetics.releasedShare(soFar / 60_000.0, e.releaseK)
    }

    /** Marks the pouch as taken out now. */
    fun finish(e: LogEntry, s: Settings, nowMs: Long): LogEntry =
        e.copy(endTimestamp = nowMs.coerceIn(e.timestamp + MIN_DURATION_MS, e.timestamp + autoMs(e, s)))

    /** When the most recent pouch came out, by hand or by its own time; null while nothing has come out. */
    fun lastOutMs(entries: List<LogEntry>, s: Settings, nowMs: Long): Long? =
        entries.asSequence()
            .filter { it.timestamp <= nowMs && !isActive(it, s, nowMs) }
            .map { min(it.timestamp + effectiveDurationMs(it, s), nowMs) }
            .maxOrNull()

    /**
     * The entry with its start moved to [newStartMs]. A recorded removal stays where it was, so fixing a
     * late tap on + does not shorten the pouch; it moves along only if it would otherwise come before the
     * new start. The tracking day follows the new start under the current rules.
     */
    fun withNewStart(e: LogEntry, newStartMs: Long, s: Settings): LogEntry {
        val end = e.endTimestamp?.let { if (it >= newStartMs + MIN_DURATION_MS) it else it + (newStartMs - e.timestamp) }
            ?.coerceIn(newStartMs + MIN_DURATION_MS, newStartMs + autoMs(e, s))
        return e.copy(timestamp = newStartMs, endTimestamp = end, day = DayKeys.epochDay(newStartMs, s.dayStartHour))
    }
}

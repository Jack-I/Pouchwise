package app.pouchwise.model

import app.pouchwise.data.LogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min

/**
 * Maps instants to "tracking days", which may start at an hour other than midnight. Boundaries follow
 * the wall clock, so a 04:00 day start stays at 04:00 on the days the clocks change.
 */
object DayKeys {
    fun epochDay(timeMs: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDateTime().minusHours(dayStartHour.toLong()).toLocalDate().toEpochDay()

    fun startOfDayMs(epochDay: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()

    fun endOfDayMs(epochDay: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOfDayMs(epochDay + 1, dayStartHour, zone)

    fun localDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /** The tracking day an entry was logged into; rows from before 0.7.7 that lack it use the current rules. */
    fun of(e: LogEntry, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        e.day ?: epochDay(e.timestamp, dayStartHour, zone)

    /** A picked time less than this ahead of now is a slip on the picker and counts as now, not as the day before. */
    private const val SLIP_MS = 3_600_000L

    /** [hour]:[minute] on [date], or the same time a day earlier when that would lie in the future beyond a slip. */
    fun pastTimeMs(date: LocalDate, hour: Int, minute: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val at = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        if (at <= nowMs + SLIP_MS) return min(at, nowMs)
        return date.minusDays(1).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * [hour]:[minute] on the day before, of or after [aroundMs], whichever is closest to it: a corrected time is near
     * the old one, even across midnight. One that would lie in the future is now.
     */
    fun nearestPastTimeMs(aroundMs: Long, hour: Int, minute: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = Instant.ofEpochMilli(aroundMs).atZone(zone).toLocalDate()
        val nearest = (-1L..1L)
            .map { date.plusDays(it).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli() }
            .minBy { abs(it - aroundMs) }
        return min(nearest, nowMs)
    }

    /**
     * New days for the pouches of the day running at [nowMs] when what "today" means changes from
     * ([oldHour], [oldZone]) to ([newHour], [newZone]). Finished days keep their pouches and take in none.
     */
    fun refiled(
        entries: List<LogEntry>,
        nowMs: Long,
        oldHour: Int,
        oldZone: ZoneId,
        newHour: Int,
        newZone: ZoneId,
    ): Map<Long, Long> {
        val running = epochDay(nowMs, oldHour, oldZone)
        val earliest = min(running, epochDay(nowMs, newHour, newZone))
        return entries
            .filter { of(it, oldHour, oldZone) >= running }
            .mapNotNull { e -> epochDay(e.timestamp, newHour, newZone).coerceAtLeast(earliest).takeIf { it != e.day }?.let { e.id to it } }
            .toMap()
    }
}

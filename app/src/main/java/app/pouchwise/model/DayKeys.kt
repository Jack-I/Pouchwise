package app.pouchwise.model

import app.pouchwise.data.LogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    /** [hour]:[minute] on [date], or the same time a day earlier when that would lie in the future. */
    fun pastTimeMs(date: LocalDate, hour: Int, minute: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val at = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        if (at <= nowMs + 60_000L) return at
        return date.minusDays(1).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }
}

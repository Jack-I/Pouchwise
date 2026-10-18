package app.pouchwise.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Maps instants to "tracking days", which may start at an hour other than midnight. */
object DayKeys {
    fun epochDay(timeMs: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(timeMs).atZone(zone).minusHours(dayStartHour.toLong()).toLocalDate().toEpochDay()

    fun startOfDayMs(epochDay: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).plusHours(dayStartHour.toLong()).toInstant().toEpochMilli()

    fun endOfDayMs(epochDay: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOfDayMs(epochDay + 1, dayStartHour, zone)

    fun localDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}

package app.pouchwise.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Number and date formatting helpers used across screens. */
object Fmt {
    /** "8", "7.5" – whole numbers without a decimal part. */
    fun mg(v: Double): String =
        if (abs(v - v.roundToInt()) < 0.05) String.format(Locale.getDefault(), "%d", v.roundToInt())
        else String.format(Locale.getDefault(), "%.1f", v)

    fun oneDecimal(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)

    fun ngMl(v: Double): String =
        if (v >= 100) String.format(Locale.getDefault(), "%d", v.roundToInt())
        else if (v >= 10) String.format(Locale.getDefault(), "%.0f", v)
        else String.format(Locale.getDefault(), "%.1f", v)

    fun percent(ratio: Double): String = String.format(Locale.getDefault(), "%d%%", (ratio * 100).roundToInt())

    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "14:05" today, otherwise "Tue 14:05". */
    fun timeOrDayTime(ms: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        val n = Instant.ofEpochMilli(nowMs).atZone(zone)
        val pattern = if (t.toLocalDate() == n.toLocalDate()) "HH:mm" else "EEE HH:mm"
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(t)
    }

    fun dateShort(epochDay: Long): String =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(LocalDate.ofEpochDay(epochDay))

    fun dateWithWeekday(epochDay: Long): String =
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()).format(LocalDate.ofEpochDay(epochDay))

    fun dateLong(epochDay: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()).format(LocalDate.ofEpochDay(epochDay))

    fun hourLabel(hour: Int): String = String.format(Locale.getDefault(), "%02d:00", hour)
}

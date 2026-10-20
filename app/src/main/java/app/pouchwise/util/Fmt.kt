package app.pouchwise.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
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

    /** "22.5 ₽", "2,480 ₽": whole units from 100 up, otherwise up to two decimals; the symbol never wraps alone. */
    fun money(v: Double, symbol: String): String {
        val n = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = if (abs(v) >= 100) 0 else 2
        }.format(v)
        return if (symbol.isEmpty()) n else "$n\u00A0$symbol"
    }

    /** "08:40" from minutes after midnight. */
    fun clockMinutes(minutes: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", Math.floorMod(minutes, 24 * 60) / 60, Math.floorMod(minutes, 60))

    /** "06:21" for a pouch timer. */
    fun mmss(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0)
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
    }

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

    /** "19 Oct 2026, 13:05" in the local zone from an ISO offset date-time, or null when it does not parse. */
    fun isoDateTime(iso: String, zone: ZoneId = ZoneId.systemDefault()): String? = runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(zone)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()))
    }.getOrNull()
}

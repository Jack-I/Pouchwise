package app.pouchwise.util

import android.content.res.Resources
import app.pouchwise.R
import app.pouchwise.model.LimitMode
import app.pouchwise.model.Limits
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

    /** "2 pouches left today", "1 pouch over the limit", "35 mg left today"… */
    fun leftToday(res: Resources, mode: LimitMode, limit: Int, count: Int, mg: Double): String {
        val left = Limits.left(mode, limit, count, mg)
        return when {
            left == 0.0 -> res.getString(R.string.limit_reached)
            mode == LimitMode.COUNT && left > 0 -> res.getQuantityString(R.plurals.pouches_left_today, left.toInt(), left.toInt())
            mode == LimitMode.COUNT -> res.getQuantityString(R.plurals.over_limit_by_pouches, -left.toInt(), -left.toInt())
            left > 0 -> res.getString(R.string.mg_left_today, Fmt.mg(left))
            else -> res.getString(R.string.over_limit_by_mg, Fmt.mg(-left))
        }
    }

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

    /** "14:05" today, "Tue 14:05" within a week either way, otherwise "14 Oct 14:05". */
    fun timeOrDayTime(ms: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        val days = abs(ChronoUnit.DAYS.between(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), t.toLocalDate()))
        val pattern = when {
            days == 0L -> "HH:mm"
            days < 7 -> "EEE HH:mm"
            else -> "d MMM HH:mm"
        }
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(t)
    }

    fun dateShort(epochDay: Long): String =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(LocalDate.ofEpochDay(epochDay))

    /** "19–25 Oct", or "26 Oct – 1 Nov" when the month changes. */
    fun dayRange(fromDay: Long, toDay: Long): String {
        val from = LocalDate.ofEpochDay(fromDay)
        val to = LocalDate.ofEpochDay(toDay)
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return if (from.month == to.month && from.year == to.year) "${from.dayOfMonth}–${dayMonth.format(to)}"
               else "${dayMonth.format(from)} – ${dayMonth.format(to)}"
    }

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

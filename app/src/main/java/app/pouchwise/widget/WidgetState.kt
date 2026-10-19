package app.pouchwise.widget

import android.content.Context
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.data.SettingsRepository
import app.pouchwise.model.DayKeys
import app.pouchwise.model.LimitMode
import app.pouchwise.model.Limits
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import kotlinx.coroutines.flow.first

/** What the home-screen widgets show: today's usage against the limit, and the pouch that is in. */
data class WidgetState(
    val mode: LimitMode,
    val count: Int,
    val mg: Double,
    val limit: Int,
    /** False while the user has not created any pouch yet, which hides the buttons. */
    val hasTypes: Boolean,
    /** Short name of the pouch in right now (the latest one when several are in), or null. */
    val inLabel: String? = null,
    /** When that pouch went in; the widget's timer counts from here. */
    val inSinceMs: Long? = null,
    /** When the next of the pouches that are in counts as removed by itself. */
    val nextAutoEndMs: Long? = null,
    /** When the next tracking day starts; the widgets refresh then. */
    val dayEndMs: Long? = null,
) {
    val used: Double get() = Limits.usage(mode, count, mg)
    val over: Boolean get() = used > limit + 1e-9
    /** Share of the limit used, capped at 1; a day with no limit counts as full once anything is logged. */
    val ratio: Float get() = when {
        limit > 0 -> (used / limit).coerceIn(0.0, 1.0).toFloat()
        used > 0 -> 1f
        else -> 0f
    }
}

/** The pouches in right now, oldest first. */
fun pouchesIn(entries: List<LogEntry>, s: Settings, nowMs: Long): List<LogEntry> =
    entries.filter { PouchTiming.isActive(it, s, nowMs) }.sortedBy { it.timestamp }

/** The widget line is narrow: the brand alone ("ZYN" for "ZYN Cool Mint"), or the name for pouches without a brand. */
fun widgetLabel(type: PouchType?): String =
    type?.brand?.trim()?.takeIf { it.isNotEmpty() } ?: type?.name ?: "?"

object WidgetData {
    suspend fun load(context: Context): WidgetState {
        val dao = AppDatabase.get(context).dao()
        val settings = SettingsRepository(context).settings.first()
        val now = System.currentTimeMillis()
        val day = DayKeys.epochDay(now, settings.dayStartHour)
        val dayEnd = DayKeys.endOfDayMs(day, settings.dayStartHour)
        val entries = dao.entriesOfDay(day, DayKeys.startOfDayMs(day, settings.dayStartHour), dayEnd)
        val inNow = pouchesInNow(context, settings, now)
        val latest = inNow.lastOrNull()
        return WidgetState(
            mode = settings.limitMode,
            count = entries.size,
            mg = entries.sumOf { it.strengthMg },
            limit = Limits.limitForDayRounded(settings, day),
            hasTypes = dao.activeTypes().isNotEmpty(),
            inLabel = latest?.let { widgetLabel(it.second) },
            inSinceMs = latest?.first?.timestamp,
            nextAutoEndMs = inNow.minOfOrNull { it.first.timestamp + PouchTiming.autoMs(it.first, settings) },
            dayEndMs = dayEnd,
        )
    }

    /** Pouches in right now with their types, oldest first. Pouches put in before the day boundary count too. */
    suspend fun pouchesInNow(context: Context, settings: Settings, nowMs: Long): List<Pair<LogEntry, PouchType?>> {
        val dao = AppDatabase.get(context).dao()
        val since = nowMs - PouchTiming.MAX_POUCH_MINUTES * 60_000L
        val entries = pouchesIn(dao.entriesBetween(since, nowMs + 1), settings, nowMs)
        if (entries.isEmpty()) return emptyList()
        val types = dao.typesByIds(entries.map { it.pouchTypeId }.distinct()).associateBy { it.id }
        return entries.map { it to types[it.pouchTypeId] }
    }
}

package app.pouchwise.widget

import android.content.Context
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.data.SettingsRepository
import app.pouchwise.model.DayKeys
import app.pouchwise.model.IntervalMode
import app.pouchwise.model.IntervalPlan
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
    /** When the interval plan has the next pouch due, if that is still ahead. */
    val nextOnPlanMs: Long? = null,
    /** With the interval plan on: none of the pouches fits under today's limit, so the next one is tomorrow. */
    val nextTomorrow: Boolean = false,
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

/** What the cover-screen widget shows besides [WidgetState]. */
data class CoverState(
    /** The pouches in right now with their types, oldest first. */
    val inNow: List<Pair<LogEntry, PouchType?>>,
    /** When the last pouch came out, by hand or by its own time; null when none has. */
    val lastOutMs: Long?,
    /** Up to three pouches for one-tap logging. */
    val quick: List<PouchType>,
)

/**
 * The pouches for the cover widget's buttons: the [max] used most in the recent past, shown in the order of
 * "Your pouches" (which [active] already has) so the buttons do not swap places from one day to the next.
 */
fun quickPicks(active: List<PouchType>, uses: Map<Long, Int>, max: Int = 3): List<PouchType> {
    val chosen = active
        .sortedWith(compareByDescending<PouchType> { uses[it.id] ?: 0 }.thenBy { it.sortOrder }.thenBy { it.id })
        .take(max)
        .map { it.id }
        .toSet()
    return active.filter { it.id in chosen }
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
        val nextOnPlan = if (settings.intervalMode == IntervalMode.OFF) null else {
            val recent = dao.entriesBetween(now - IntervalPlan.DATA_MS, now + 1)
            IntervalPlan.threshold(recent, settings, now)
                ?.let { IntervalPlan.nextOnPlanMs(recent.map { PouchTiming.toDose(it, settings) }, settings.pk, it, now) }
                ?.takeIf { it > now }
        }
        val count = entries.size
        val mg = entries.sumOf { it.strengthMg }
        val limit = Limits.limitForDayRounded(settings, day)
        val types = dao.activeTypes()
        return WidgetState(
            mode = settings.limitMode,
            count = count,
            mg = mg,
            limit = limit,
            hasTypes = types.isNotEmpty(),
            inLabel = latest?.let { widgetLabel(it.second) },
            inSinceMs = latest?.first?.timestamp,
            nextAutoEndMs = inNow.minOfOrNull { it.first.timestamp + PouchTiming.autoMs(it.first, settings) },
            dayEndMs = dayEnd,
            nextOnPlanMs = nextOnPlan,
            nextTomorrow = settings.intervalMode != IntervalMode.OFF &&
                Limits.noneFit(settings, limit, count, mg, types.map { it.strengthMg }),
        )
    }

    suspend fun loadCover(context: Context): CoverState {
        val dao = AppDatabase.get(context).dao()
        val settings = SettingsRepository(context).settings.first()
        val now = System.currentTimeMillis()
        val recent = dao.entriesBetween(now - LAST_OUT_LOOKBACK_MS, now + 1)
        val uses = dao.usageSince(now - QUICK_LOOKBACK_MS).associate { it.pouchTypeId to it.uses }
        return CoverState(
            inNow = pouchesInNow(context, settings, now),
            lastOutMs = PouchTiming.lastOutMs(recent, settings, now),
            quick = quickPicks(dao.activeTypes(), uses),
        )
    }

    private const val LAST_OUT_LOOKBACK_MS = 3L * 24 * 3_600_000L
    private const val QUICK_LOOKBACK_MS = 14L * 24 * 3_600_000L

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

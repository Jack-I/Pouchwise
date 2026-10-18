package app.pouchwise.widget

import android.content.Context
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.PouchDao
import app.pouchwise.data.PouchType
import app.pouchwise.data.SettingsRepository
import app.pouchwise.model.DayKeys
import app.pouchwise.model.LimitMode
import app.pouchwise.model.Limits
import kotlinx.coroutines.flow.first

/** What the home-screen widgets show: today's usage against the limit. */
data class WidgetState(
    val mode: LimitMode,
    val count: Int,
    val mg: Double,
    val limit: Int,
    /** Name of the pouch the "+" button logs, or null when there is nothing to log. */
    val defaultTypeName: String?,
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

object WidgetData {
    suspend fun load(context: Context): WidgetState {
        val dao = AppDatabase.get(context).dao()
        val settings = SettingsRepository(context).settings.first()
        val now = System.currentTimeMillis()
        val day = DayKeys.epochDay(now, settings.dayStartHour)
        val entries = dao.entriesBetween(
            DayKeys.startOfDayMs(day, settings.dayStartHour),
            DayKeys.endOfDayMs(day, settings.dayStartHour),
        )
        return WidgetState(
            mode = settings.limitMode,
            count = entries.size,
            mg = entries.sumOf { it.strengthMg },
            limit = Limits.limitForDayRounded(settings, day),
            defaultTypeName = defaultType(dao)?.name,
        )
    }

    /** The pouch the widget's "+" logs: the one used last if still visible, otherwise the first in the list. */
    suspend fun defaultType(dao: PouchDao): PouchType? {
        val types = dao.activeTypes()
        if (types.isEmpty()) return null
        val latest = dao.latestEntry()
        return types.firstOrNull { it.id == latest?.pouchTypeId } ?: types.first()
    }
}

package app.pouchwise.data

import android.content.Context
import androidx.room.withTransaction
import app.pouchwise.model.DayKeys
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Keeps the running day's pouches in the running day when what "today" means changes: a new day-start
 * hour or a new time zone. Finished days keep their pouches.
 */
object RunningDay {
    suspend fun refile(context: Context, oldHour: Int, oldZone: ZoneId, newHour: Int, newZone: ZoneId) {
        val db = AppDatabase.get(context)
        val dao = db.dao()
        val now = System.currentTimeMillis()
        db.withTransaction {
            val running = DayKeys.epochDay(now, oldHour, oldZone)
            DayKeys.refiled(dao.entriesFromDay(running), now, oldHour, oldZone, newHour, newZone)
                .forEach { (id, day) -> dao.setDay(id, day) }
        }
    }

    /** Run on every app start and on the system's time-zone broadcast. */
    suspend fun checkZone(context: Context) {
        val repo = SettingsRepository(context)
        val zone = ZoneId.systemDefault()
        if (repo.settings.first().zoneId == zone.id) return
        val (before, _) = repo.update { it.copy(zoneId = zone.id) }
        if (before.zoneId.isEmpty() || before.zoneId == zone.id) return
        val oldZone = runCatching { ZoneId.of(before.zoneId) }.getOrNull() ?: return
        refile(context, before.dayStartHour, oldZone, before.dayStartHour, zone)
    }
}

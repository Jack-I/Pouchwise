package app.pouchwise.data

import android.content.Context
import androidx.room.withTransaction
import app.pouchwise.model.DayKeys
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

    /**
     * Run on every app start and widget refresh. The pouches move before the new zone is saved, so a failure is tried
     * again next time; moving them twice changes nothing.
     */
    suspend fun checkZone(context: Context): Unit = withContext(NonCancellable) {
        val repo = SettingsRepository(context)
        val zone = ZoneId.systemDefault()
        val saved = repo.settings.first()
        if (saved.zoneId == zone.id) return@withContext
        val oldZone = saved.zoneId.takeIf { it.isNotEmpty() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        if (oldZone != null) refile(context, saved.dayStartHour, oldZone, saved.dayStartHour, zone)
        repo.update { if (it.zoneId == saved.zoneId) it.copy(zoneId = zone.id) else it }
    }
}

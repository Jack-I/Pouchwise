package app.pouchwise.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pushes fresh state to every placed widget; cheap no-op when none is placed. */
object WidgetUpdater {
    /** Sent to [WideWidgetProvider] when a pouch that is in reaches its pouch time. */
    const val ACTION_REFRESH = "app.pouchwise.action.REFRESH_WIDGETS"

    suspend fun updateAll(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return@withContext
        val ringIds = manager.getAppWidgetIds(ComponentName(app, RingWidgetProvider::class.java))
        val wideIds = manager.getAppWidgetIds(ComponentName(app, WideWidgetProvider::class.java))
        if (ringIds.isEmpty() && wideIds.isEmpty()) {
            scheduleRefresh(app, null)
            return@withContext
        }
        val state = WidgetData.load(app)
        ringIds.forEach { manager.updateAppWidget(it, WidgetRenderer.ringViews(app, state)) }
        wideIds.forEach { manager.updateAppWidget(it, WidgetRenderer.wideViews(app, state)) }
        scheduleRefresh(app, if (wideIds.isNotEmpty()) state.nextAutoEndMs else null)
    }

    private fun scheduleRefresh(context: Context, atMs: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, WideWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Inexact and non-waking, so no alarm permission is needed; Android may deliver it a few minutes late.
        if (atMs == null) alarms.cancel(pending) else alarms.set(AlarmManager.RTC, atMs + 1_000L, pending)
    }
}

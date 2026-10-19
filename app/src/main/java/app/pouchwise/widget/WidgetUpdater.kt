package app.pouchwise.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Pushes fresh state to every placed widget; cheap no-op when none is placed. */
object WidgetUpdater {
    /** Sent to [WideWidgetProvider] when a new tracking day starts or a pouch that is in reaches its pouch time. */
    const val ACTION_REFRESH = "app.pouchwise.action.REFRESH_WIDGETS"

    private const val ALARM_WINDOW_MS = 10 * 60_000L
    private const val MAX_BITMAP_PX = 720

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
        ringIds.forEach { id ->
            val (w, h) = sizePx(app, manager, id)
            manager.updateAppWidget(id, WidgetRenderer.ringViews(app, state, w, h))
        }
        wideIds.forEach { manager.updateAppWidget(it, WidgetRenderer.wideViews(app, state)) }
        // Only the wide widget shows the pouch that is in; both show today's numbers.
        val autoEnd = if (wideIds.isNotEmpty()) state.nextAutoEndMs else null
        scheduleRefresh(app, listOfNotNull(autoEnd, state.dayEndMs).minOrNull())
    }

    /** The widget's size on screen, from what the launcher reports: portrait uses min width × max height. */
    private fun sizePx(context: Context, manager: AppWidgetManager, id: Int): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(id)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val wDp = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val hDp = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val density = context.resources.displayMetrics.density
        val w = ((if (wDp > 0) wDp else 74) * density).roundToInt().coerceIn(48, MAX_BITMAP_PX)
        val h = ((if (hDp > 0) hDp else 80) * density).roundToInt().coerceIn(48, MAX_BITMAP_PX)
        return w to h
    }

    private fun scheduleRefresh(context: Context, atMs: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, WideWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Non-waking, no alarm permission needed; the window keeps it within ten minutes of the moment.
        if (atMs == null) alarms.cancel(pending)
        else alarms.setWindow(AlarmManager.RTC, atMs + 1_000L, ALARM_WINDOW_MS, pending)
    }
}

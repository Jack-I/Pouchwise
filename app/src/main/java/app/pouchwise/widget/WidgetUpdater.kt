package app.pouchwise.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pushes fresh state to every placed widget; cheap no-op when none is placed. */
object WidgetUpdater {
    suspend fun updateAll(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return@withContext
        val ringIds = manager.getAppWidgetIds(ComponentName(app, RingWidgetProvider::class.java))
        val wideIds = manager.getAppWidgetIds(ComponentName(app, WideWidgetProvider::class.java))
        if (ringIds.isEmpty() && wideIds.isEmpty()) return@withContext
        val state = WidgetData.load(app)
        ringIds.forEach { manager.updateAppWidget(it, WidgetRenderer.ringViews(app, state)) }
        wideIds.forEach { manager.updateAppWidget(it, WidgetRenderer.wideViews(app, state)) }
    }
}

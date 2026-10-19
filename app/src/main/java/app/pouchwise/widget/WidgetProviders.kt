package app.pouchwise.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 1×1: the logo inside a ring that fills up towards the daily limit and turns red beyond it, with the app name. */
class RingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }

    /** The picture is drawn at the widget's size, so redraw when the launcher reports a new one. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        refreshAsync(context)
    }
}

/** 2×1: the ring, today's count against the limit, the pouch that is in with its timer, and "+" / "✓". */
class WideWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Our alarm, and system changes that move "today" or the language of the texts.
            WidgetUpdater.ACTION_REFRESH,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> refreshAsync(context)
            else -> super.onReceive(context, intent)
        }
    }
}

private fun BroadcastReceiver.refreshAsync(context: Context) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // A failed refresh must not crash the app from a background broadcast; the next one will retry.
            runCatching { WidgetUpdater.updateAll(context) }
        } finally {
            pending.finish()
        }
    }
}

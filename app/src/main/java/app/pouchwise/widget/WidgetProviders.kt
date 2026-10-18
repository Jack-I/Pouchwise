package app.pouchwise.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 1×1: the logo inside a ring that fills up towards the daily limit and turns red beyond it. */
class RingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }
}

/** 2×1: the ring plus today's count, the limit and a "+" that opens the pouch chooser. */
class WideWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }
}

private fun BroadcastReceiver.refreshAsync(context: Context) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            WidgetUpdater.updateAll(context)
        } finally {
            pending.finish()
        }
    }
}

package app.pouchwise.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.pouchwise.data.RunningDay
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
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> refreshAsync(context)
            Intent.ACTION_TIMEZONE_CHANGED -> refreshAsync(context, zoneChanged = true)
            else -> super.onReceive(context, intent)
        }
    }
}

/**
 * Galaxy Z Flip cover screen (Samsung gives such widgets the whole page): today against the limit, the pouch
 * that is in with ✓, and buttons for the three pouches used most lately.
 */
class CoverWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG) return super.onReceive(context, intent)
        val typeId = intent.getLongExtra(EXTRA_TYPE_ID, -1L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { QuickLog.logById(context, typeId) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG = "app.pouchwise.action.COVER_LOG"
        const val EXTRA_TYPE_ID = "type_id"
    }
}

private fun BroadcastReceiver.refreshAsync(context: Context, zoneChanged: Boolean = false) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // A failure must not crash the app from a background broadcast; the next one will retry.
            if (zoneChanged) runCatching { RunningDay.checkZone(context) }
            runCatching { WidgetUpdater.updateAll(context) }
        } finally {
            pending.finish()
        }
    }
}

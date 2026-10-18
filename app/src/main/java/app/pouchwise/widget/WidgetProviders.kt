package app.pouchwise.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.pouchwise.R
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 1×1: the logo inside a ring that fills up towards the daily limit and turns red beyond it. */
class RingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }
}

/** 2×1: the ring plus today's count, the limit and a "+" that logs the usual pouch. */
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

/** Not exported: only our own PendingIntents reach it. Logs the usual pouch from the wide widget. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG_DEFAULT) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).dao()
                val type = WidgetData.defaultType(dao)
                if (type != null) {
                    dao.insertEntry(
                        LogEntry(
                            pouchTypeId = type.id,
                            timestamp = System.currentTimeMillis(),
                            strengthMg = type.strengthMg,
                            releaseK = type.releaseK,
                        ),
                    )
                    val text = context.getString(R.string.widget_logged, type.name)
                    Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
                }
                WidgetUpdater.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG_DEFAULT = "app.pouchwise.widget.LOG_DEFAULT"

        fun logDefaultIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 1,
                Intent(context, WidgetActionReceiver::class.java).setAction(ACTION_LOG_DEFAULT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

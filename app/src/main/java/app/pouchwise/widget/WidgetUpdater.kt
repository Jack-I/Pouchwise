package app.pouchwise.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Pushes fresh state to every placed widget; cheap no-op when none is placed. */
object WidgetUpdater {
    /** Sent to [WideWidgetProvider] when a new tracking day starts or a pouch that is in reaches its pouch time. */
    const val ACTION_REFRESH = "app.pouchwise.action.REFRESH_WIDGETS"

    private const val ALARM_WINDOW_MS = 10 * 60_000L
    private const val MAX_BITMAP_PX = 720
    /** Pictures for several sizes travel in one call to the launcher; past this it sends just one. */
    private const val MAX_PICTURES_BYTES = 800 * 1024L

    /** One update at a time: an older one finishing last would show stale numbers and set a stale alarm. */
    private val lock = Mutex()

    suspend fun updateAll(context: Context) = lock.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return@withContext
            val ringIds = manager.getAppWidgetIds(ComponentName(app, RingWidgetProvider::class.java))
            val wideIds = manager.getAppWidgetIds(ComponentName(app, WideWidgetProvider::class.java))
            val coverIds = manager.getAppWidgetIds(ComponentName(app, CoverWidgetProvider::class.java))
            if (ringIds.isEmpty() && wideIds.isEmpty() && coverIds.isEmpty()) {
                scheduleRefresh(app, null)
                return@withContext
            }
            val state = WidgetData.load(app)
            ringIds.forEach { id -> updateRing(app, manager, id, state) }
            wideIds.forEach { manager.updateAppWidget(it, WidgetRenderer.wideViews(app, state)) }
            if (coverIds.isNotEmpty()) {
                val cover = WidgetData.loadCover(app)
                coverIds.forEach { manager.updateAppWidget(it, WidgetRenderer.coverViews(app, state, cover)) }
            }
            // The 1×1 shows only today's numbers; the others also show the pouch that is in.
            val autoEnd = if (wideIds.isNotEmpty() || coverIds.isNotEmpty()) state.nextAutoEndMs else null
            scheduleRefresh(app, listOfNotNull(autoEnd, state.dayEndMs).minOrNull())
        }
    }

    /**
     * The 1×1 picture is drawn at the cell's size. The launcher gets one per size it may show (portrait and
     * landscape) and picks the right one itself, whichever way the phone is held while this runs.
     */
    private fun updateRing(context: Context, manager: AppWidgetManager, id: Int, state: WidgetState) {
        val sizes = cellSizesDp(manager.getAppWidgetOptions(id))
        val density = context.resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).roundToInt().coerceIn(48, MAX_BITMAP_PX)
        val bytes = sizes.sumOf { px(it.width).toLong() * px(it.height) * 4 }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.size > 1 && bytes <= MAX_PICTURES_BYTES) {
            val bySize = RemoteViews(sizes.associateWith { WidgetRenderer.ringViews(context, state, px(it.width), px(it.height)) })
            // Too big for the launcher after all: one picture is better than none.
            if (runCatching { manager.updateAppWidget(id, bySize) }.isSuccess) return
        }
        val portrait = sizes.first()
        manager.updateAppWidget(id, WidgetRenderer.ringViews(context, state, px(portrait.width), px(portrait.height)))
    }

    /** Sizes the launcher reports for the cell, portrait (narrow and tall) first. */
    private fun cellSizesDp(options: Bundle): List<SizeF> {
        val reported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
        } else emptyList()
        fun dp(key: String, fallback: Int) = options.getInt(key).let { if (it > 0) it else fallback }.toFloat()
        val portrait = SizeF(dp(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 74), dp(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 80))
        val landscape = SizeF(dp(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 74), dp(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80))
        val all = reported.filter { it.width > 0f && it.height > 0f }.ifEmpty { listOf(portrait, landscape) }
        return all.distinct().sortedBy { it.width / it.height }.take(4)
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

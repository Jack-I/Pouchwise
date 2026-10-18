package app.pouchwise.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import app.pouchwise.MainActivity
import app.pouchwise.R
import app.pouchwise.model.LimitMode
import app.pouchwise.util.Fmt

/** Draws the ring bitmaps and builds the RemoteViews for both widgets. */
object WidgetRenderer {
    private const val BLUE = 0xFF1E88E5.toInt()
    private const val RED_DISC = 0xFFB71C1C.toInt()
    private const val RED_RING = 0xFFFF8A80.toInt()

    /** Share of the 108 dp foreground canvas the drawing occupies, and its rightward nudge (see design/make_icon.py). */
    private const val ART_SHARE = 62f / 108f
    private const val ART_SHIFT = 3f / 108f

    fun ring(context: Context, sizePx: Int, state: WidgetState): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = sizePx / 2f

        paint.style = Paint.Style.FILL
        paint.color = if (state.over) RED_DISC else BLUE
        c.drawCircle(cx, cx, cx, paint)

        val stroke = sizePx * 0.09f
        val inset = stroke / 2f + sizePx * 0.04f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(70, 255, 255, 255)
        c.drawArc(rect, 0f, 360f, false, paint)
        val sweep = if (state.over) 360f else state.ratio * 360f
        if (sweep > 0f) {
            paint.color = if (state.over) RED_RING else Color.WHITE
            c.drawArc(rect, -90f, sweep, false, paint)
        }

        val logo = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_foreground)
        if (logo != null) {
            val inner = sizePx - 2f * (inset + stroke / 2f)
            val full = inner * 0.74f / ART_SHARE
            val left = cx - full / 2f - full * ART_SHIFT
            val top = cx - full / 2f
            logo.setBounds(left.toInt(), top.toInt(), (left + full).toInt(), (top + full).toInt())
            logo.draw(c)
        }
        return bmp
    }

    fun ringViews(context: Context, state: WidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_ring).apply {
            setImageViewBitmap(R.id.widget_ring_image, ring(context, 256, state))
            setContentDescription(R.id.widget_ring_image, countText(context, state))
            setOnClickPendingIntent(R.id.widget_ring_root, openApp(context))
        }

    fun wideViews(context: Context, state: WidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_wide).apply {
            setImageViewBitmap(R.id.widget_wide_ring, ring(context, 192, state))
            setInt(R.id.widget_wide_root, "setBackgroundResource", if (state.over) R.drawable.widget_bg_over else R.drawable.widget_bg)
            setTextViewText(R.id.widget_wide_count, countText(context, state))
            setTextViewText(
                R.id.widget_wide_sub,
                context.getString(
                    when {
                        state.over -> R.string.widget_over
                        state.mode == LimitMode.COUNT -> R.string.widget_pouches_today
                        else -> R.string.widget_mg_today
                    },
                ),
            )
            setOnClickPendingIntent(R.id.widget_wide_root, openApp(context))
            if (state.defaultTypeName != null) {
                setViewVisibility(R.id.widget_wide_add, View.VISIBLE)
                setContentDescription(R.id.widget_wide_add, context.getString(R.string.widget_add, state.defaultTypeName))
                setOnClickPendingIntent(R.id.widget_wide_add, WidgetActionReceiver.logDefaultIntent(context))
            } else {
                setViewVisibility(R.id.widget_wide_add, View.GONE)
            }
        }

    private fun countText(context: Context, state: WidgetState): String = when (state.mode) {
        LimitMode.COUNT -> "${state.count} / ${state.limit}"
        LimitMode.MG -> "${Fmt.mg(state.mg)} / ${state.limit} ${context.getString(R.string.unit_mg)}"
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

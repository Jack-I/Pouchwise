package app.pouchwise.widget

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import app.pouchwise.MainActivity
import app.pouchwise.R
import app.pouchwise.model.LimitMode
import app.pouchwise.ui.theme.pouchColor
import app.pouchwise.util.Fmt
import kotlin.math.roundToInt

/** Draws the ring bitmaps and builds the RemoteViews for both widgets. */
object WidgetRenderer {
    private const val BLUE = 0xFF1E88E5.toInt()
    private const val RED_DISC = 0xFFB71C1C.toInt()
    private const val RED_RING = 0xFFFF8A80.toInt()

    /** Share of the 108 dp foreground canvas the drawing occupies (see design/make_icon.py). */
    private const val ART_SHARE = 56f / 108f

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
            val left = cx - full / 2f
            val top = cx - full / 2f
            logo.setBounds(left.toInt(), top.toInt(), (left + full).toInt(), (top + full).toInt())
            logo.draw(c)
        }
        return bmp
    }

    /**
     * The 1×1 widget as one picture: the ring with the app name under it, drawn at the widget's own size.
     * A TextView there was clipped by launcher fonts drawing beyond their declared metrics; here the label
     * gets room measured from its actual ink and shrinks to fit the width if it has to.
     */
    fun ringWithLabel(context: Context, widthPx: Int, heightPx: Int, state: WidgetState): Bitmap {
        val dm = context.resources.displayMetrics
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val label = context.getString(R.string.app_name)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, dm)
            setShadowLayer(2f * dm.density, 0f, dm.density, 0x59000000)
        }
        val maxTextWidth = widthPx * 0.96f
        val textWidth = paint.measureText(label)
        if (textWidth > maxTextWidth) paint.textSize *= maxTextWidth / textWidth

        val ink = Rect().also { paint.getTextBounds(label, 0, label.length, it) }
        val fm = paint.fontMetrics
        val above = maxOf(-fm.top, -ink.top.toFloat())
        val below = maxOf(fm.bottom, ink.bottom.toFloat()) // also holds the 1 dp-low shadow of a word without descenders
        val pad = 2f * dm.density
        val baseline = heightPx - dm.density - below
        val textTop = baseline - above

        val ringSize = minOf(widthPx - 2 * pad, textTop - 2 * pad).toInt().coerceAtLeast(1)
        c.drawBitmap(ring(context, ringSize, state), (widthPx - ringSize) / 2f, pad, null)
        c.drawText(label, widthPx / 2f, baseline, paint)
        return bmp
    }

    fun ringViews(context: Context, state: WidgetState, widthPx: Int, heightPx: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_ring).apply {
            setImageViewBitmap(R.id.widget_ring_image, ringWithLabel(context, widthPx, heightPx, state))
            setContentDescription(R.id.widget_ring_image, context.getString(R.string.app_name) + ": " + countText(context, state))
            setOnClickPendingIntent(R.id.widget_ring_root, openApp(context))
        }

    /** The cover-screen page: ring and today's numbers, the pouch that is in, and three pouch buttons. */
    fun coverViews(context: Context, state: WidgetState, cover: CoverState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_cover).apply {
            val now = System.currentTimeMillis()
            val muted = 0xD9FFFFFF.toInt()
            setImageViewBitmap(R.id.cover_ring, ring(context, (84 * context.resources.displayMetrics.density).roundToInt(), state))
            setTextViewText(R.id.cover_count, countText(context, state))
            setTextColor(R.id.cover_count, if (state.over) RED_TEXT else Color.WHITE)
            setTextViewText(R.id.cover_left, leftText(context, state))
            setTextColor(R.id.cover_left, if (state.over) RED_TEXT else muted)
            setContentDescription(R.id.cover_top, context.getString(R.string.app_name) + ": " + countText(context, state))
            setOnClickPendingIntent(R.id.cover_top, openApp(context))

            val latest = cover.inNow.lastOrNull()
            if (latest == null) {
                setViewVisibility(R.id.cover_status_dot, View.GONE)
                setChronometer(R.id.cover_status_timer, SystemClock.elapsedRealtime(), null, false)
                setViewVisibility(R.id.cover_status_timer, View.GONE)
                setViewVisibility(R.id.cover_check, View.GONE)
                setTextColor(R.id.cover_status_text, muted)
                val last = cover.lastOutMs?.let { context.getString(R.string.cover_last_out_at, Fmt.timeOrDayTime(it, now)) }
                    ?: context.getString(R.string.cover_nothing_yet)
                val next = state.nextOnPlanMs?.let { " · " + context.getString(R.string.cover_next, Fmt.time(it)) }.orEmpty()
                setTextViewText(R.id.cover_status_text, last + next)
            } else {
                val (entry, type) = latest
                setViewVisibility(R.id.cover_status_dot, View.VISIBLE)
                setInt(R.id.cover_status_dot, "setColorFilter", pouchColor(type?.colorIndex ?: 0).toArgb())
                setTextColor(R.id.cover_status_text, Color.WHITE)
                setTextViewText(
                    R.id.cover_status_text,
                    if (cover.inNow.size == 1) context.getString(R.string.cover_in_now, "${widgetLabel(type)} ${mgText(context, entry.strengthMg)}")
                    else context.resources.getQuantityString(R.plurals.cover_pouches_in, cover.inNow.size, cover.inNow.size),
                )
                // The launcher runs the timer itself, on the elapsed-realtime clock.
                setChronometer(R.id.cover_status_timer, SystemClock.elapsedRealtime() - (now - entry.timestamp).coerceAtLeast(0L), null, true)
                setViewVisibility(R.id.cover_status_timer, View.VISIBLE)
                setViewVisibility(R.id.cover_check, View.VISIBLE)
                setContentDescription(R.id.cover_check, context.getString(R.string.widget_remove))
                setOnClickPendingIntent(R.id.cover_check, removeOnCover(context))
            }

            COVER_SLOTS.forEachIndexed { i, slot ->
                val type = cover.quick.getOrNull(i)
                if (type == null) {
                    setViewVisibility(slot.root, View.INVISIBLE)
                    return@forEachIndexed
                }
                val color = pouchColor(type.colorIndex).toArgb()
                val label = "${widgetLabel(type)} ${mgText(context, type.strengthMg)}"
                setViewVisibility(slot.root, View.VISIBLE)
                setInt(slot.bg, "setColorFilter", blend(color, COVER_BUTTON_BASE, 0.3f))
                setInt(slot.dot, "setColorFilter", color)
                setTextViewText(slot.name, widgetLabel(type))
                setTextViewText(slot.mg, mgText(context, type.strengthMg))
                setContentDescription(slot.root, context.getString(R.string.cover_log, label))
                setOnClickPendingIntent(slot.root, logFromCover(context, type.id))
            }
        }

    private class CoverSlot(val root: Int, val bg: Int, val dot: Int, val name: Int, val mg: Int)

    private val COVER_SLOTS = listOf(
        CoverSlot(R.id.cover_btn_0, R.id.cover_btn_0_bg, R.id.cover_btn_0_dot, R.id.cover_btn_0_name, R.id.cover_btn_0_mg),
        CoverSlot(R.id.cover_btn_1, R.id.cover_btn_1_bg, R.id.cover_btn_1_dot, R.id.cover_btn_1_name, R.id.cover_btn_1_mg),
        CoverSlot(R.id.cover_btn_2, R.id.cover_btn_2_bg, R.id.cover_btn_2_dot, R.id.cover_btn_2_name, R.id.cover_btn_2_mg),
    )

    private const val RED_TEXT = 0xFFFF8A80.toInt()
    private const val COVER_BUTTON_BASE = 0xFF121A24.toInt()

    /** Samsung's cover screen is display 1 (checked on a Flip7), so the chooser opens there, not inside. */
    private const val COVER_DISPLAY_ID = 1

    /** "2 pouches left today", "1 pouch over the limit", "35 mg left today"… */
    private fun leftText(context: Context, state: WidgetState): String {
        val res = context.resources
        return when (state.mode) {
            LimitMode.COUNT -> {
                val diff = state.count - state.limit
                when {
                    diff > 0 -> res.getQuantityString(R.plurals.over_limit_by_pouches, diff, diff)
                    diff == 0 -> context.getString(R.string.limit_reached)
                    else -> res.getQuantityString(R.plurals.pouches_left_today, -diff, -diff)
                }
            }
            LimitMode.MG -> {
                val diff = state.mg - state.limit
                when {
                    diff > 1e-9 -> context.getString(R.string.over_limit_by_mg, Fmt.mg(diff))
                    diff > -1e-9 -> context.getString(R.string.limit_reached)
                    else -> context.getString(R.string.mg_left_today, Fmt.mg(-diff))
                }
            }
        }
    }

    private fun mgText(context: Context, mg: Double): String = context.getString(R.string.mg_value, Fmt.mg(mg))

    private fun blend(color: Int, base: Int, share: Float): Int {
        fun channel(shift: Int) =
            (((color shr shift) and 0xFF) * share + ((base shr shift) and 0xFF) * (1 - share)).roundToInt()
        return Color.rgb(channel(16), channel(8), channel(0))
    }

    private fun removeOnCover(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 3,
            Intent(context, QuickLogActivity::class.java)
                .setAction(QuickLogActivity.ACTION_REMOVE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ActivityOptions.makeBasic().setLaunchDisplayId(COVER_DISPLAY_ID).toBundle(),
        )

    /** One per pouch: the request code keeps them apart, since extras do not. */
    private fun logFromCover(context: Context, typeId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, 1000 + typeId.toInt(),
            Intent(context, CoverWidgetProvider::class.java)
                .setAction(CoverWidgetProvider.ACTION_LOG)
                .putExtra(CoverWidgetProvider.EXTRA_TYPE_ID, typeId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun wideViews(context: Context, state: WidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_wide).apply {
            setImageViewBitmap(R.id.widget_wide_ring, ring(context, 192, state))
            setContentDescription(R.id.widget_wide_ring, countText(context, state))
            setInt(R.id.widget_wide_root, "setBackgroundResource", if (state.over) R.drawable.widget_bg_over else R.drawable.widget_bg)
            setTextViewText(R.id.widget_wide_count, countWithLimit(state))
            setOnClickPendingIntent(R.id.widget_wide_root, openApp(context))

            val since = state.inSinceMs
            if (since != null) {
                setTextViewText(R.id.widget_wide_sub, state.inLabel.orEmpty())
                // The launcher runs the chronometer itself, on the elapsed-realtime clock.
                val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - since).coerceAtLeast(0L)
                setChronometer(R.id.widget_wide_timer, base, null, true)
                setViewVisibility(R.id.widget_wide_timer, View.VISIBLE)
            } else {
                setTextViewText(
                    R.id.widget_wide_sub,
                    state.nextOnPlanMs?.let { context.getString(R.string.widget_next, Fmt.time(it)) }
                        ?: context.getString(if (state.mode == LimitMode.COUNT) R.string.widget_unit_pouches else R.string.widget_unit_mg_today),
                )
                setChronometer(R.id.widget_wide_timer, SystemClock.elapsedRealtime(), null, false)
                setViewVisibility(R.id.widget_wide_timer, View.GONE)
            }

            // With every pouch hidden, "+" opens the app; "✓" must stay while one is still in.
            if (state.hasTypes || since != null) {
                setViewVisibility(R.id.widget_wide_buttons, View.VISIBLE)
                // Set here rather than in the layout so they follow the app's own language.
                setContentDescription(R.id.widget_wide_add, context.getString(R.string.widget_add))
                setContentDescription(R.id.widget_wide_remove, context.getString(R.string.widget_remove))
                setOnClickPendingIntent(R.id.widget_wide_add, quickAction(context, QuickLogActivity.ACTION_LOG, 1))
                val anyIn = since != null
                setInt(R.id.widget_wide_remove, "setBackgroundResource", if (anyIn) R.drawable.widget_btn_bg else R.drawable.widget_btn_bg_off)
                setImageViewResource(R.id.widget_wide_remove, if (anyIn) R.drawable.ic_widget_check else R.drawable.ic_widget_check_off)
                setOnClickPendingIntent(R.id.widget_wide_remove, quickAction(context, QuickLogActivity.ACTION_REMOVE, 2))
            } else {
                setViewVisibility(R.id.widget_wide_buttons, View.GONE)
            }
        }

    /** "+" and "✓" open a small chooser over the home screen, or act straight away when there is nothing to choose. */
    private fun quickAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, QuickLogActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** "39/60": today's amount large, the limit small; the unit goes on the line below. */
    private fun countWithLimit(state: WidgetState): CharSequence {
        val used = when (state.mode) {
            LimitMode.COUNT -> state.count.toString()
            LimitMode.MG -> Fmt.mg(state.mg)
        }
        val text = SpannableStringBuilder(used)
        val start = text.length
        text.append("/${state.limit}")
        text.setSpan(RelativeSizeSpan(0.6f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(0xD9FFFFFF.toInt()), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
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

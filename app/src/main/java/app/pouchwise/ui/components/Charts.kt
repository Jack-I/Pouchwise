package app.pouchwise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pouchwise.model.BodyLevels
import app.pouchwise.model.DayStat
import app.pouchwise.model.LimitMode
import app.pouchwise.util.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max

private enum class Align { START, CENTER, END }

private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    style: TextStyle,
    align: Align = Align.START,
    baselineBottom: Boolean = false,
) {
    val layout = measurer.measure(AnnotatedString(text), style = style)
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val left = when (align) {
        Align.START -> x
        Align.CENTER -> x - w / 2f
        Align.END -> x - w
    }
    val top = if (baselineBottom) y - h else y
    drawText(layout, topLeft = Offset(left, top))
}

private fun niceStep(maxValue: Double, targetTicks: Int = 4): Double {
    if (maxValue <= 0) return 1.0
    val raw = maxValue / targetTicks
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val norm = raw / mag
    val nice = when {
        norm <= 1 -> 1.0
        norm <= 2 -> 2.0
        norm <= 2.5 -> 2.5
        norm <= 5 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

/** Body levels around [nowMs], sorted by time: [past] ends and [future] starts at [nowMs]. */
data class BodySpan(
    val nowMs: Long,
    val past: List<Pair<Long, BodyLevels>>,
    val future: List<Pair<Long, BodyLevels>>,
)

/**
 * Nicotine (left scale) and cotinine (right scale) in a window [windowMs] wide that the user drags between
 * [startMs] and [endMs]. [endOffsetH] is where the window ends, in hours after [BodySpan.nowMs]. Solid lines
 * show the past, dashed ones the forecast if nothing more is taken; the scales follow the visible part.
 */
@Composable
fun ScrollableBodyChart(
    span: BodySpan,
    startMs: Long,
    endMs: Long,
    windowMs: Long,
    endOffsetH: Animatable<Float, AnimationVector1D>,
    nicotineColor: Color,
    cotinineColor: Color,
    leftAxisTitle: String,
    rightAxisTitle: String,
    modifier: Modifier = Modifier,
    /** The interval plan's level (mg), drawn as a line, and when the forecast drops below it. */
    planLevelMg: Double? = null,
    nextOnPlanMs: Long? = null,
    planColor: Color = Color.Unspecified,
    planLabel: String = "",
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val nicLabelStyle = labelStyle.copy(color = nicotineColor)
    val cotLabelStyle = labelStyle.copy(color = cotinineColor)
    val planLabelStyle = labelStyle.copy(color = planColor)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.onSurfaceVariant
    val zone = remember { ZoneId.systemDefault() }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val padL = with(density) { 44.dp.toPx() }
    val padR = with(density) { 48.dp.toPx() }
    var widthPx by remember { mutableIntStateOf(0) }
    val nowMs = span.nowMs
    val windowH = windowMs / 3_600_000f
    val hoursPerPx = if (widthPx > padL + padR) windowH / (widthPx - padL - padR) else 0f

    // Bounds clamp the value themselves, and only when no fling is running.
    LaunchedEffect(startMs, endMs, windowMs, nowMs) {
        val minEnd = (startMs - nowMs) / 3_600_000f + windowH
        val maxEnd = max((endMs - nowMs) / 3_600_000f, minEnd)
        endOffsetH.updateBounds(minEnd, maxEnd)
    }

    val t1 = nowMs + (endOffsetH.value * 3_600_000f).toLong()
    val t0 = t1 - windowMs
    val past = slice(span.past, t0, t1)
    val future = slice(span.future, t0, t1)
    fun peak(pick: (BodyLevels) -> Double) =
        max(past.maxOfOrNull { pick(it.second) } ?: 0.0, future.maxOfOrNull { pick(it.second) } ?: 0.0)
    val nTarget = max(max(peak { it.nicotineMg }, (planLevelMg ?: 0.0) * 1.15), 0.5)
    val cTarget = max(peak { it.cotinineMg }, 2.0)
    val nStep = niceStep(nTarget)
    val cStep = niceStep(cTarget)
    val nMax by animateFloatAsState((ceil(nTarget / nStep) * nStep).toFloat(), label = "nicotine scale")
    val cMax by animateFloatAsState((ceil(cTarget / cStep) * cStep).toFloat(), label = "cotinine scale")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(272.dp)
            .onSizeChanged { widthPx = it.width }
            .draggable(
                state = rememberDraggableState { delta -> scope.launch { endOffsetH.snapTo(endOffsetH.value - delta * hoursPerPx) } },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity -> endOffsetH.animateDecay(-velocity * hoursPerPx, exponentialDecay()) },
            ),
    ) {
        val padT = 24.dp.toPx()
        val padB = 26.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        if (w <= 0f || h <= 0f || nMax <= 0f || cMax <= 0f) return@Canvas

        label(measurer, leftAxisTitle, 0f, 0f, nicLabelStyle, Align.START)
        label(measurer, rightAxisTitle, size.width, 0f, cotLabelStyle, Align.END)

        fun x(t: Long) = padL + (t - t0).toFloat() / windowMs.toFloat() * w
        fun yN(v: Double) = padT + h - (v / nMax).toFloat() * h
        fun yC(v: Double) = padT + h - (v / cMax).toFloat() * h

        var v = 0.0
        while (v <= nMax + 1e-6) {
            val yy = yN(v)
            drawLine(gridColor, Offset(padL, yy), Offset(padL + w, yy), strokeWidth = 1f)
            label(measurer, Fmt.mg(v), padL - 6.dp.toPx(), yy - 7.sp.toPx(), nicLabelStyle, Align.END)
            v += nStep
        }
        var c = 0.0
        while (c <= cMax + 1e-6) {
            label(measurer, Fmt.mg(c), padL + w + 6.dp.toPx(), yC(c) - 7.sp.toPx(), cotLabelStyle, Align.START)
            c += cStep
        }

        // x ticks on the local clock; midnight shows the day, so a window dragged into the past says which one
        val tickH = when {
            windowH <= 15 -> 3L
            windowH <= 30 -> 6L
            windowH <= 60 -> 12L
            windowH <= 100 -> 24L
            else -> 48L
        }
        val firstDay = Instant.ofEpochMilli(t0).atZone(zone).toLocalDate()
        var tick = (if (tickH == 48L) firstDay.minusDays(Math.floorMod(firstDay.toEpochDay(), 2L)) else firstDay).atStartOfDay()
        while (true) {
            val at = tick.atZone(zone)
            val ms = at.toInstant().toEpochMilli()
            if (ms > t1) break
            if (ms >= t0) {
                val xx = x(ms)
                drawLine(gridColor, Offset(xx, padT), Offset(xx, padT + h), strokeWidth = 1f)
                val text = if (at.hour == 0 && at.minute == 0) dayFmt.format(at) else timeFmt.format(at)
                label(measurer, text, xx, padT + h + 6.dp.toPx(), labelStyle, Align.CENTER)
            }
            tick = tick.plusHours(tickH)
        }

        fun path(points: List<Pair<Long, BodyLevels>>, pick: (BodyLevels) -> Double, y: (Double) -> Float): Path {
            val p = Path()
            points.forEachIndexed { i, (t, l) ->
                if (i == 0) p.moveTo(x(t), y(pick(l))) else p.lineTo(x(t), y(pick(l)))
            }
            return p
        }
        val solid = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val dashed = Stroke(
            width = 3.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
        )
        clipRect(padL, padT - 8.dp.toPx(), padL + w, padT + h + 2.dp.toPx()) {
            if (past.size >= 2) {
                drawPath(path(past, { it.cotinineMg }, ::yC), cotinineColor, style = solid)
                drawPath(path(past, { it.nicotineMg }, ::yN), nicotineColor, style = solid)
            }
            if (future.size >= 2) {
                drawPath(path(future, { it.cotinineMg }, ::yC), cotinineColor.copy(alpha = 0.8f), style = dashed)
                drawPath(path(future, { it.nicotineMg }, ::yN), nicotineColor.copy(alpha = 0.8f), style = dashed)
            }
            if (planLevelMg != null) {
                val yp = yN(planLevelMg)
                drawLine(
                    planColor, Offset(padL, yp), Offset(padL + w, yp), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                )
                label(measurer, planLabel, padL + w - 4.dp.toPx(), yp - 16.sp.toPx(), planLabelStyle, Align.END)
                if (nextOnPlanMs != null && nextOnPlanMs in t0..t1) {
                    val xp = x(nextOnPlanMs)
                    drawCircle(planColor, radius = 5.dp.toPx(), center = Offset(xp, yp))
                    label(measurer, Fmt.time(nextOnPlanMs), xp, yp + 8.dp.toPx(), planLabelStyle, Align.CENTER)
                }
            }
            if (nowMs in t0..t1) {
                val xn = x(nowMs)
                drawLine(
                    nowColor, Offset(xn, padT), Offset(xn, padT + h), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                )
                val lv = (span.past.lastOrNull() ?: span.future.firstOrNull())?.second
                if (lv != null) {
                    drawCircle(cotinineColor, radius = 5.dp.toPx(), center = Offset(xn, yC(lv.cotinineMg)))
                    drawCircle(nicotineColor, radius = 5.dp.toPx(), center = Offset(xn, yN(lv.nicotineMg)))
                }
            }
        }
    }
}

/** Plain bars, labelled under every [labelEvery]th one. */
@Composable
fun SimpleBars(values: List<Double>, labels: List<String>, color: Color, modifier: Modifier = Modifier, labelEvery: Int = 1) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        if (values.isEmpty()) return@Canvas
        val labelH = 16.dp.toPx()
        val h = size.height - labelH
        val slot = size.width / values.size
        val top = values.max().takeIf { it > 0 } ?: 1.0
        values.forEachIndexed { i, v ->
            val bh = (v / top).toFloat() * (h - 2.dp.toPx())
            if (bh > 0f) {
                drawRoundRect(
                    color, topLeft = Offset(i * slot + slot * 0.15f, h - bh), size = Size(slot * 0.7f, bh),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            if (i % labelEvery == 0) label(measurer, labels[i], i * slot + slot / 2, h + 3.dp.toPx(), style, Align.CENTER)
        }
    }
}

/** A line through [values], lowest at the bottom; for trends where the exact numbers are shown elsewhere. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min()
        val span = (values.max() - lo).takeIf { it > 0 } ?: 1.0
        val pad = 3.dp.toPx()
        val path = Path()
        values.forEachIndexed { i, v ->
            val px = pad + i * (size.width - 2 * pad) / (values.size - 1)
            val py = size.height - pad - ((v - lo) / span).toFloat() * (size.height - 2 * pad)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** The points inside [t0, t1] plus one on each side, so the lines run to the edges. */
private fun slice(points: List<Pair<Long, BodyLevels>>, t0: Long, t1: Long): List<Pair<Long, BodyLevels>> {
    if (points.isEmpty()) return points
    val from = points.binarySearchBy(t0) { it.first }.let { if (it < 0) -it - 2 else it }.coerceIn(0, points.lastIndex)
    val to = points.binarySearchBy(t1) { it.first }.let { if (it < 0) -it - 1 else it }.coerceIn(from, points.lastIndex)
    return points.subList(from, to + 1)
}

/**
 * Bars of daily usage with the limit as a line; [future] continues the limit line past today.
 */
@Composable
fun DailyBarChart(
    stats: List<DayStat>,
    futureLimits: List<Pair<Long, Int>>,
    todayDay: Long,
    /** Today's unit. Bars use it for every day; the limit line is drawn only for days whose plan used it too. */
    mode: LimitMode,
    barColor: Color,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    fun value(st: DayStat): Double = if (mode == LimitMode.COUNT) st.count.toDouble() else st.mg
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val overColor = MaterialTheme.colorScheme.error

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        if (stats.isEmpty()) return@Canvas
        val padL = 36.dp.toPx()
        val padR = 8.dp.toPx()
        val padT = 24.dp.toPx()
        val padB = 24.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val days = stats.size + futureLimits.size
        val slot = w / days
        val barW = slot * 0.55f

        val maxUsed = stats.maxOf { value(it) }
        val maxLimit = max(stats.filter { it.mode == mode }.maxOfOrNull { it.limit } ?: 0, futureLimits.maxOfOrNull { it.second } ?: 0)
        val rawMax = max(max(maxUsed, maxLimit.toDouble()), 1.0)
        val step = niceStep(rawMax)
        val yMax = ceil(rawMax * 1.1 / step) * step

        fun xCenter(i: Int) = padL + slot * i + slot / 2f
        fun y(v: Double) = padT + h - (v / yMax).toFloat() * h

        var v = 0.0
        while (v <= yMax + 1e-9) {
            val yy = y(v)
            drawLine(gridColor, Offset(padL, yy), Offset(padL + w, yy), strokeWidth = 1f)
            label(measurer, Fmt.mg(v), padL - 6.dp.toPx(), yy - 7.sp.toPx(), labelStyle, Align.END)
            v += step
        }

        stats.forEachIndexed { i, st ->
            if (value(st) <= 0) return@forEachIndexed
            val top = y(value(st))
            // Colour by the day's own verdict, made under the plan of that day.
            val color = if (st.withinLimit) barColor else overColor
            drawRoundRect(
                color = color,
                topLeft = Offset(xCenter(i) - barW / 2f, top),
                size = Size(barW, padT + h - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2.5f, barW / 2.5f),
            )
        }

        // limit line across past and future days; gaps where a past day counted in the other unit
        val limitPath = Path()
        var idx = 0
        var penDown = false
        stats.forEach { st ->
            if (st.mode == mode) {
                val px = xCenter(idx); val py = y(st.limit.toDouble())
                if (penDown) limitPath.lineTo(px, py) else limitPath.moveTo(px, py)
                penDown = true
            } else {
                penDown = false
            }
            idx++
        }
        futureLimits.forEach { (_, lim) ->
            val px = xCenter(idx); val py = y(lim.toDouble())
            if (penDown) limitPath.lineTo(px, py) else limitPath.moveTo(px, py)
            penDown = true
            idx++
        }
        drawPath(limitPath, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // today marker and label
        val todayIdx = stats.indexOfFirst { it.epochDay == todayDay }
        if (todayIdx >= 0) {
            val xt = xCenter(todayIdx)
            drawLine(
                gridColor, Offset(xt, padT - 6.dp.toPx()), Offset(xt, padT + h), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
            val lim = stats[todayIdx].limit
            drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(xt, y(lim.toDouble())))
            label(measurer, Fmt.mg(lim.toDouble()), xt, y(lim.toDouble()) - 20.sp.toPx(), labelStyle.copy(color = lineColor), Align.CENTER)
        }

        // date labels: first, middle, today
        val labelIdx = listOf(0, stats.size / 2, todayIdx).filter { it >= 0 }.distinct()
        labelIdx.forEach { i ->
            label(measurer, Fmt.dateShort(stats[i].epochDay), xCenter(i), padT + h + 6.dp.toPx(), labelStyle, Align.CENTER)
        }
    }
}

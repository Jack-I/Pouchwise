package app.pouchwise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pouchwise.model.BodyLevels
import app.pouchwise.model.DayStat
import app.pouchwise.model.LimitMode
import app.pouchwise.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

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
 * The part of the Body chart on screen: a window [windowH] hours wide that ends [endH] hours after "now".
 * Drags, flings, pinches and the window buttons all move it through one [MutatorMutex], so a new touch stops
 * a glide still running. The window stays within [reachH] (hours from now to the first and last data) where
 * its width allows, and "now" stays reachable at [nowAt] of the way across.
 */
@Stable
class BodyChartView(windowH: Float, private val nowAt: Float, private val zoomH: ClosedFloatingPointRange<Float>) {
    var windowH by mutableFloatStateOf(windowH)
        private set
    var endH by mutableFloatStateOf(windowH * (1 - nowAt))
        private set
    var reachH by mutableStateOf(0f..0f)
    private val mutex = MutatorMutex()

    /** Where the window ends with "now" at [nowAt] of the way across. */
    fun nowEndH(w: Float = windowH) = w * (1 - nowAt)

    private fun endRange(w: Float): ClosedFloatingPointRange<Float> {
        val lo = min(-w * nowAt, reachH.start) + w
        return lo..max(max(nowEndH(w), reachH.endInclusive), lo)
    }

    private fun place(w: Float, end: Float) {
        windowH = w
        endH = end.coerceIn(endRange(w))
    }

    /** Pulls the window back inside the data after the data or "now" moved. */
    fun settle() {
        endH = endH.coerceIn(endRange(windowH))
    }

    suspend fun dragBy(hours: Float) = mutex.mutate { place(windowH, endH + hours) }

    suspend fun fling(hoursPerSecond: Float) = mutex.mutate {
        var last = 0f
        AnimationState(0f, hoursPerSecond).animateDecay(exponentialDecay()) {
            val before = endH
            place(windowH, endH + (value - last))
            last = value
            if (endH == before) cancelAnimation()
        }
    }

    /**
     * Two fingers moved [zoom] times further apart around [focus] (0 = the plot's left edge, 1 = its right) and
     * together by [pan] plot widths: the time under the fingers stays under them.
     */
    suspend fun pinch(focus: Float, zoom: Float, pan: Float) = mutex.mutate {
        val atFocus = endH - windowH + focus * windowH
        val w = (windowH / zoom).coerceIn(zoomH)
        place(w, atFocus + (1 - focus) * w - pan * w)
    }

    /** To a window [w] hours wide, around the middle of the screen, or with "now" kept at [nowAt] if [aroundNow]. */
    suspend fun zoomTo(w: Float, aroundNow: Boolean) = glide(w, if (aroundNow) nowEndH(w) else endH - windowH / 2 + w / 2)

    suspend fun goToNow() = glide(windowH, nowEndH())

    private suspend fun glide(w: Float, end: Float) = mutex.mutate {
        val w0 = windowH
        val e0 = endH
        val e1 = end.coerceIn(endRange(w))
        animate(0f, 1f, animationSpec = tween(350)) { p, _ -> place(w0 + (w - w0) * p, e0 + (e1 - e0) * p) }
    }
}

/**
 * Nicotine (left scale) and cotinine (right scale) in the window that [view] describes, dragged with one finger
 * and zoomed with two. Solid lines show the past, dashed ones the forecast if nothing more is taken. The scales
 * fit the visible part and one window either side of it, so a peak is allowed for before it scrolls into view,
 * and they glide when they change.
 */
@Composable
fun ScrollableBodyChart(
    span: BodySpan,
    view: BodyChartView,
    nicotineColor: Color,
    cotinineColor: Color,
    leftAxisTitle: String,
    rightAxisTitle: String,
    modifier: Modifier = Modifier,
    /** The tolerance plan's level (mg), drawn as a line, and when the forecast drops below it. */
    planLevelMg: Double? = null,
    nextOnPlanMs: Long? = null,
    planColor: Color = Color.Unspecified,
    /** Written over the "now" line where it does not run into the axis titles. */
    nowLabel: String = "",
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val nicLabelStyle = labelStyle.copy(color = nicotineColor)
    val cotLabelStyle = labelStyle.copy(color = cotinineColor)
    val planLabelStyle = labelStyle.copy(color = planColor)
    val nowLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
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
    val windowH = view.windowH
    val windowMs = (windowH * 3_600_000f).toLong()
    val plotW = widthPx - padL - padR

    val t1 = nowMs + (view.endH * 3_600_000f).toLong()
    val t0 = t1 - windowMs
    val past = slice(span.past, t0, t1)
    val future = slice(span.future, t0, t1)
    val nearPast = slice(span.past, t0 - windowMs, t1 + windowMs)
    val nearFuture = slice(span.future, t0 - windowMs, t1 + windowMs)
    fun peak(pick: (BodyLevels) -> Double) =
        max(nearPast.maxOfOrNull { pick(it.second) } ?: 0.0, nearFuture.maxOfOrNull { pick(it.second) } ?: 0.0)
    val nTarget = max(max(peak { it.nicotineMg }, (planLevelMg ?: 0.0) * 1.15), 0.5)
    val cTarget = max(peak { it.cotinineMg }, 2.0)
    val nStep = niceStep(nTarget)
    val cStep = niceStep(cTarget)
    val glide = spring<Float>(stiffness = Spring.StiffnessLow)
    val nMax by animateFloatAsState((ceil(nTarget / nStep) * nStep).toFloat(), glide, label = "nicotine scale")
    val cMax by animateFloatAsState((ceil(cTarget / cStep) * cStep).toFloat(), glide, label = "cotinine scale")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(272.dp)
            .onSizeChanged { widthPx = it.width }
            // Two fingers zoom. Taken in the Initial pass, so the one-finger drag below and the page's
            // vertical scroll see the events consumed and stay out of it.
            .pointerInput(view) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinched = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        val width = size.width - padL - padR
                        if (pressed >= 2 && width > 0f) {
                            pinched = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan().x / width
                            val focus = ((event.calculateCentroid(useCurrent = true).x - padL) / width).coerceIn(0f, 1f)
                            if (zoom != 1f || pan != 0f) scope.launch { view.pinch(focus, zoom, pan) }
                            event.changes.forEach { it.consume() }
                        } else if (pinched && pressed == 1 && width > 0f) {
                            // The finger left after a pinch keeps moving the chart.
                            val change = event.changes.first { it.pressed }
                            val dx = change.positionChange().x
                            if (dx != 0f) scope.launch { view.dragBy(-dx / width * view.windowH) }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    if (plotW > 0f) scope.launch { view.dragBy(-delta / plotW * view.windowH) }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity -> if (plotW > 0f) view.fling(-velocity / plotW * view.windowH) },
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
            windowH <= 4.5f -> 1L
            windowH <= 8.5f -> 2L
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
                if (nextOnPlanMs != null && nextOnPlanMs in t0..t1) {
                    val xp = x(nextOnPlanMs)
                    drawCircle(planColor, radius = 5.dp.toPx(), center = Offset(xp, yp))
                    label(measurer, Fmt.time(nextOnPlanMs), xp, yp + 8.dp.toPx(), planLabelStyle, Align.CENTER)
                }
            }
        }
        if (nowMs in t0..t1) {
            val xn = x(nowMs)
            val nowText = measurer.measure(AnnotatedString(nowLabel), style = nowLabelStyle)
            val half = nowText.size.width / 2f
            val gap = 6.dp.toPx()
            val leftEnd = measurer.measure(AnnotatedString(leftAxisTitle), style = nicLabelStyle).size.width + gap
            val rightStart = size.width - measurer.measure(AnnotatedString(rightAxisTitle), style = cotLabelStyle).size.width - gap
            val labelled = nowLabel.isNotEmpty() && xn - half > leftEnd && xn + half < rightStart
            if (labelled) drawText(nowText, topLeft = Offset(xn - half, 0f))
            drawLine(
                nowColor, Offset(xn, if (labelled) nowText.size.height + 2.dp.toPx() else padT), Offset(xn, padT + h),
                strokeWidth = 1.5.dp.toPx(),
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

/** Width of one day in [DailyBarChart]: about a dozen days fit on a phone. */
private const val DAY_SLOT_DP = 22

/**
 * Bars of daily usage with the limit as a line, dragged sideways through the whole history and the days of
 * [futureLimits] ahead. Every day carries its date and weekday; today starts two thirds of the way across.
 * One scale covers all days, so bars compare across the history.
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
    val todayStyle = labelStyle.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    val aheadStyle = labelStyle.copy(color = labelStyle.color.copy(alpha = 0.6f))
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val overColor = MaterialTheme.colorScheme.error
    val locale = remember { Locale.getDefault() }
    val monthFmt = remember { DateTimeFormatter.ofPattern("LLLL", locale) }
    fun monthName(d: LocalDate) = monthFmt.format(d).replaceFirstChar { it.titlecase(locale) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val slot = with(density) { DAY_SLOT_DP.dp.toPx() }
    val padL = with(density) { 36.dp.toPx() }
    var widthPx by remember { mutableIntStateOf(0) }
    val days = stats.size + futureLimits.size
    val shown = if (widthPx > padL) (widthPx - padL) / slot else 0f
    val todayIdx = stats.indexOfFirst { it.epochDay == todayDay }.takeIf { it >= 0 } ?: stats.lastIndex

    val scale = remember(stats, futureLimits, mode) {
        val maxUsed = stats.maxOfOrNull { value(it) } ?: 0.0
        val maxLimit = max(stats.filter { it.mode == mode }.maxOfOrNull { it.limit } ?: 0, futureLimits.maxOfOrNull { it.second } ?: 0)
        val rawMax = max(max(maxUsed, maxLimit.toDouble()), 1.0)
        val step = niceStep(rawMax)
        step to ceil(rawMax * 1.1 / step) * step
    }

    // The slot at the left edge, fractional; dragging moves it.
    val first = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(days, shown) {
        if (shown <= 0f) return@LaunchedEffect
        first.updateBounds(0f, max(days - shown, 0f))
        if (!placed) {
            first.snapTo(todayIdx + 0.5f - shown * 2f / 3f)
            placed = true
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(232.dp)
            .onSizeChanged { widthPx = it.width }
            .draggable(
                state = rememberDraggableState { delta -> scope.launch { first.snapTo(first.value - delta / slot) } },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity -> first.animateDecay(-velocity / slot, exponentialDecay()) },
            ),
    ) {
        if (stats.isEmpty()) return@Canvas
        val padT = 22.dp.toPx()
        val padB = 36.dp.toPx()
        val h = size.height - padT - padB
        val (step, yMax) = scale
        val firstDay = stats.first().epochDay
        val offset = first.value
        val barW = slot * 0.55f

        fun xCenter(i: Int) = padL + (i - offset) * slot + slot / 2f
        fun y(v: Double) = padT + h - (v / yMax).toFloat() * h
        fun limitAt(i: Int): Int? =
            if (i < stats.size) stats[i].takeIf { it.mode == mode }?.limit else futureLimits[i - stats.size].second

        var v = 0.0
        while (v <= yMax + 1e-9) {
            val yy = y(v)
            drawLine(gridColor, Offset(padL, yy), Offset(size.width, yy), strokeWidth = 1f)
            label(measurer, Fmt.mg(v), padL - 6.dp.toPx(), yy - 7.sp.toPx(), labelStyle, Align.END)
            v += step
        }

        val from = (floor(offset).toInt() - 1).coerceAtLeast(0)
        val to = min(days - 1, ceil(offset + shown).toInt())
        clipRect(left = padL, top = 0f, right = size.width, bottom = size.height) {
            for (i in from..min(to, stats.lastIndex)) {
                val st = stats[i]
                if (value(st) <= 0) continue
                val top = y(value(st))
                // Colour by the day's own verdict, made under the plan of that day.
                drawRoundRect(
                    color = if (st.withinLimit) barColor else overColor,
                    topLeft = Offset(xCenter(i) - barW / 2f, top),
                    size = Size(barW, padT + h - top),
                    cornerRadius = CornerRadius(barW / 2.5f, barW / 2.5f),
                )
            }

            // limit line; gaps where a past day counted in the other unit
            val limitPath = Path()
            var penDown = false
            for (i in from..to) {
                val lim = limitAt(i)
                if (lim == null) {
                    penDown = false
                    continue
                }
                val px = xCenter(i)
                val py = y(lim.toDouble())
                if (penDown) limitPath.lineTo(px, py) else limitPath.moveTo(px, py)
                penDown = true
            }
            drawPath(limitPath, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            if (todayIdx in from..to) {
                val xt = xCenter(todayIdx)
                drawLine(
                    gridColor, Offset(xt, padT - 6.dp.toPx()), Offset(xt, padT + h), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                )
                val lim = stats[todayIdx].limit
                drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(xt, y(lim.toDouble())))
                label(measurer, Fmt.mg(lim.toDouble()), xt, y(lim.toDouble()) - 20.sp.toPx(), labelStyle.copy(color = lineColor), Align.CENTER)
            }

            // The month of the leftmost day stays over the left edge; the next one's name stands over its first
            // day once that is clear of it.
            val leftMonth = monthName(LocalDate.ofEpochDay(firstDay + floor(offset).toInt().coerceIn(0, days - 1)))
            val leftEnd = padL + 2.dp.toPx() + measurer.measure(AnnotatedString(leftMonth), style = labelStyle).size.width + 8.dp.toPx()
            label(measurer, leftMonth, padL + 2.dp.toPx(), 0f, labelStyle)
            for (i in from..to) {
                val date = LocalDate.ofEpochDay(firstDay + i)
                val style = when {
                    i == todayIdx -> todayStyle
                    i > todayIdx -> aheadStyle
                    else -> labelStyle
                }
                val xc = xCenter(i)
                label(measurer, date.dayOfMonth.toString(), xc, padT + h + 6.dp.toPx(), style, Align.CENTER)
                label(
                    measurer, date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                    xc, padT + h + 20.dp.toPx(), style.copy(fontSize = 10.sp), Align.CENTER,
                )
                val monthAt = xc - slot / 2f + 2.dp.toPx()
                if (date.dayOfMonth == 1 && monthAt >= leftEnd) label(measurer, monthName(date), monthAt, 0f, labelStyle)
            }
        }
    }
}

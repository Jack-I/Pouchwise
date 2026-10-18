package app.pouchwise.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pouchwise.model.BodyLevels
import app.pouchwise.model.DayStat
import app.pouchwise.util.Fmt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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

/**
 * Nicotine (left axis) and cotinine (right axis) over time. [past] ends at now, [future] starts at now
 * and is drawn dashed.
 */
@Composable
fun BodyChart(
    past: List<Pair<Long, BodyLevels>>,
    future: List<Pair<Long, BodyLevels>>,
    nowMs: Long,
    nicotineColor: Color,
    cotinineColor: Color,
    leftAxisTitle: String,
    rightAxisTitle: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val nicLabelStyle = labelStyle.copy(color = nicotineColor)
    val cotLabelStyle = labelStyle.copy(color = cotinineColor)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.onSurfaceVariant
    val zone = remember { ZoneId.systemDefault() }
    val all = past + future

    Canvas(modifier.fillMaxWidth().height(272.dp)) {
        if (all.size < 2) return@Canvas
        val padL = 44.dp.toPx()
        val padR = 48.dp.toPx()
        val padT = 24.dp.toPx()
        val padB = 26.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val t0 = all.first().first
        val t1 = all.last().first
        if (t1 <= t0) return@Canvas

        // axis titles: nicotine on the left scale, cotinine on the right one
        label(measurer, leftAxisTitle, 0f, 0f, nicLabelStyle, Align.START)
        label(measurer, rightAxisTitle, size.width, 0f, cotLabelStyle, Align.END)

        val nMaxRaw = max(all.maxOf { it.second.nicotineMg }, 0.5)
        val cMaxRaw = max(all.maxOf { it.second.cotinineMg }, 2.0)
        val nStep = niceStep(nMaxRaw)
        val cStep = niceStep(cMaxRaw)
        val nMax = ceil(nMaxRaw / nStep) * nStep
        val cMax = ceil(cMaxRaw / cStep) * cStep

        fun x(t: Long) = padL + (t - t0).toFloat() / (t1 - t0).toFloat() * w
        fun yN(v: Double) = padT + h - (v / nMax).toFloat() * h
        fun yC(v: Double) = padT + h - (v / cMax).toFloat() * h

        // horizontal grid with both axes
        var v = 0.0
        while (v <= nMax + 1e-9) {
            val yy = yN(v)
            drawLine(gridColor, Offset(padL, yy), Offset(padL + w, yy), strokeWidth = 1f)
            label(measurer, Fmt.mg(v), padL - 6.dp.toPx(), yy - 7.sp.toPx(), nicLabelStyle, Align.END)
            v += nStep
        }
        var c = 0.0
        while (c <= cMax + 1e-9) {
            val yy = yC(c)
            label(measurer, Fmt.mg(c), padL + w + 6.dp.toPx(), yy - 7.sp.toPx(), cotLabelStyle, Align.START)
            c += cStep
        }

        // x ticks aligned to local clock
        val spanH = (t1 - t0) / 3_600_000.0
        val tickMs = when {
            spanH <= 30 -> 6L * 3_600_000L
            spanH <= 60 -> 12L * 3_600_000L
            spanH <= 100 -> 24L * 3_600_000L
            else -> 48L * 3_600_000L
        }
        val offsetMs = zone.rules.getOffset(Instant.ofEpochMilli(nowMs)).totalSeconds * 1000L
        var tick = Math.floorDiv(t0 + offsetMs, tickMs) * tickMs - offsetMs
        if (tick < t0) tick += tickMs
        val fmt = if (tickMs < 24L * 3_600_000L) DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                  else DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
        while (tick <= t1) {
            val xx = x(tick)
            drawLine(gridColor, Offset(xx, padT), Offset(xx, padT + h), strokeWidth = 1f)
            val text = fmt.format(Instant.ofEpochMilli(tick).atZone(zone))
            label(measurer, text, xx, padT + h + 6.dp.toPx(), labelStyle, Align.CENTER)
            tick += tickMs
        }

        // series
        fun path(points: List<Pair<Long, BodyLevels>>, pick: (BodyLevels) -> Double, y: (Double) -> Float): Path {
            val p = Path()
            points.forEachIndexed { i, (t, l) ->
                val px = x(t)
                val py = y(pick(l))
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            return p
        }
        val solid = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val dashed = Stroke(
            width = 3.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
        )
        if (past.size >= 2) {
            drawPath(path(past, { it.cotinineMg }, ::yC), cotinineColor, style = solid)
            drawPath(path(past, { it.nicotineMg }, ::yN), nicotineColor, style = solid)
        }
        if (future.size >= 2) {
            drawPath(path(future, { it.cotinineMg }, ::yC), cotinineColor.copy(alpha = 0.8f), style = dashed)
            drawPath(path(future, { it.nicotineMg }, ::yN), nicotineColor.copy(alpha = 0.8f), style = dashed)
        }

        // now marker
        if (nowMs in t0..t1) {
            val xn = x(nowMs)
            drawLine(
                nowColor, Offset(xn, padT), Offset(xn, padT + h), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
            val lv = (past.lastOrNull() ?: future.firstOrNull())?.second
            if (lv != null) {
                drawCircle(cotinineColor, radius = 5.dp.toPx(), center = Offset(xn, yC(lv.cotinineMg)))
                drawCircle(nicotineColor, radius = 5.dp.toPx(), center = Offset(xn, yN(lv.nicotineMg)))
            }
        }
    }
}

/**
 * Bars of daily usage with the limit as a line; [future] continues the limit line past today.
 */
@Composable
fun DailyBarChart(
    stats: List<DayStat>,
    futureLimits: List<Pair<Long, Int>>,
    todayDay: Long,
    barColor: Color,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
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

        val maxUsed = stats.maxOf { it.used }
        val maxLimit = max(stats.maxOf { it.limit }, futureLimits.maxOfOrNull { it.second } ?: 0)
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
            if (st.used <= 0) return@forEachIndexed
            val top = y(st.used)
            val color = if (st.withinLimit) barColor else overColor
            drawRoundRect(
                color = color,
                topLeft = Offset(xCenter(i) - barW / 2f, top),
                size = Size(barW, padT + h - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2.5f, barW / 2.5f),
            )
        }

        // limit line across past and future days
        val limitPath = Path()
        var idx = 0
        stats.forEach { st ->
            val px = xCenter(idx); val py = y(st.limit.toDouble())
            if (idx == 0) limitPath.moveTo(px, py) else limitPath.lineTo(px, py)
            idx++
        }
        futureLimits.forEach { (_, lim) ->
            limitPath.lineTo(xCenter(idx), y(lim.toDouble()))
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

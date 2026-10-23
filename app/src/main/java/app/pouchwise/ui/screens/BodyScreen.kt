package app.pouchwise.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.model.Clearance
import app.pouchwise.model.DayKeys
import app.pouchwise.model.IntervalMode
import app.pouchwise.model.IntervalPlan
import app.pouchwise.model.Levels
import app.pouchwise.model.Pharmacokinetics
import app.pouchwise.model.PouchTiming
import app.pouchwise.ui.components.*
import app.pouchwise.ui.theme.AppColors
import app.pouchwise.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val HOUR_MS = 3_600_000L
/** The window buttons, in hours; two fingers zoom anywhere in [ZOOM_H]. */
private val PRESETS_H = listOf(8f, 48f, 7f * 24)
private val ZOOM_H = 2f..7f * 24
/**
 * Chart points every 6 min plus every pouch in and out, light enough for 37 days; every 2 min over the two days
 * before now and the day after, where the chart is most often zoomed in close.
 */
private const val SAMPLE_STEP_MS = 6 * 60_000L
private const val FINE_STEP_MS = 2 * 60_000L
private const val FINE_BACK_MS = 48 * HOUR_MS
private const val FINE_AHEAD_MS = 24 * HOUR_MS
private const val HISTORY_MAX_MS = 30L * 24 * HOUR_MS
private const val FORECAST_MAX_MS = 7L * 24 * HOUR_MS
/** Where "now" sits in a freshly opened window: two thirds past, one third forecast. */
private const val NOW_AT = 2.0 / 3.0

@Composable
fun BodyScreen(vm: AppViewModel, onOpenLevels: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val pk = settings.pk

    var savedWindowH by rememberSaveable { mutableFloatStateOf(PRESETS_H[0]) }
    val view = remember { BodyChartView(savedWindowH, NOW_AT.toFloat(), ZOOM_H) }
    LaunchedEffect(view) { snapshotFlow { view.windowH }.collect { savedWindowH = it } }
    val preset by remember(view) { derivedStateOf { PRESETS_H.indexOfFirst { abs(view.windowH / it - 1f) < 0.1f } } }
    val awayFromNow by remember(view) { derivedStateOf { abs(view.endH - view.nowEndH()) > view.windowH * 0.02f } }
    val scope = rememberCoroutineScope()

    // The ticker only moves every 30 s; a pouch logged since then is already in.
    val clock = remember(now, entries) { maxOf(now, System.currentTimeMillis()) }
    val doses = remember(entries, settings) { entries.map { PouchTiming.toDose(it, settings) } }
    val levels = remember(doses, pk, clock) { Pharmacokinetics.levelsAt(clock, doses, pk) }
    val mostlyGone by produceState<Clearance?>(initialValue = null, doses, pk, clock) {
        value = withContext(Dispatchers.Default) { Pharmacokinetics.mostlyGone(clock, doses, pk) }
    }
    val nonUserAt by produceState<Long?>(initialValue = null, doses, settings, clock) {
        value = withContext(Dispatchers.Default) { Levels.nonUserAtMs(clock, doses, pk, settings.bodyWeightKg) ?: CLEARED }
    }
    // Receptors are more than half free below this level; how long they were today, and the interval plan.
    val today = DayKeys.epochDay(clock, settings.dayStartHour)
    val restMs by produceState<Long?>(initialValue = null, doses, settings, clock) {
        value = withContext(Dispatchers.Default) {
            IntervalPlan.restMs(doses, pk, IntervalPlan.restLevelMg(settings), DayKeys.startOfDayMs(today, settings.dayStartHour), clock)
        }
    }
    val planLevel = remember(entries, settings, today) {
        IntervalPlan.threshold(entries.filter { it.timestamp >= clock - IntervalPlan.DATA_MS }, settings, clock)
    }
    val nextOnPlan = remember(doses, pk, planLevel, clock) { planLevel?.let { IntervalPlan.nextOnPlanMs(doses, pk, it, clock) } }

    // The chart can be dragged back to the first pouch (30 days at most) and ahead until both have cleared
    // (7 days at most). One set of points serves every window size; computed off the main thread.
    val firstMs = entries.firstOrNull()?.timestamp
    val reach by produceState<Reach?>(initialValue = null, doses, pk, clock) {
        value = withContext(Dispatchers.Default) {
            val cleared = Pharmacokinetics.clearance(clock, doses, pk, cotinineThreshold = Levels.nonUserCotinineMg(settings.bodyWeightKg))
            val clearEnd = max(cleared.nicotineClearMs ?: clock, cleared.cotinineClearMs ?: clock)
            val earliest = max(clock - HISTORY_MAX_MS, (firstMs ?: clock) - HOUR_MS)
            val latest = clock + (clearEnd - clock).coerceIn(2 * HOUR_MS, FORECAST_MAX_MS)
            val widest = (ZOOM_H.endInclusive * HOUR_MS).toLong()
            val from = min(earliest, clock - (widest * NOW_AT).toLong())
            val to = max(latest, clock + (widest * (1 - NOW_AT)).toLong())
            fun points(a: Long, b: Long, step: Long) = Pharmacokinetics.series(a, b, step, doses, pk)
            val fineFrom = max(from, clock - FINE_BACK_MS)
            val fineTo = min(to, clock + FINE_AHEAD_MS)
            Reach(
                earliest, latest,
                BodySpan(
                    nowMs = clock,
                    past = points(from, fineFrom, SAMPLE_STEP_MS) + points(fineFrom, clock, FINE_STEP_MS),
                    future = points(clock, fineTo, FINE_STEP_MS) + points(fineTo, to, SAMPLE_STEP_MS),
                ),
            )
        }
    }
    LaunchedEffect(reach) {
        val r = reach ?: return@LaunchedEffect
        view.reachH = (r.earliestMs - r.span.nowMs) / HOUR_MS.toFloat()..(r.latestMs - r.span.nowMs) / HOUR_MS.toFloat()
        view.settle()
    }

    val nicNg = Pharmacokinetics.toNgPerMl(levels.nicotineMg, settings.bodyWeightKg, Pharmacokinetics.NICOTINE_VD_L_PER_KG)
    val cotNg = Pharmacokinetics.toNgPerMl(levels.cotinineMg, settings.bodyWeightKg, Pharmacokinetics.COTININE_VD_L_PER_KG)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.body_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        SoftCard(padding = 16.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SubstanceSummary(
                    name = stringResource(R.string.nicotine),
                    color = AppColors.nicotine,
                    mg = levels.nicotineMg,
                    ngMl = nicNg,
                    until = mostlyGone?.let { it.nicotineClearMs ?: CLEARED },
                    untilRes = R.string.gone_95_short,
                    reachedRes = R.string.cleared_short,
                    nowMs = clock,
                    modifier = Modifier.weight(1f),
                )
                SubstanceSummary(
                    name = stringResource(R.string.cotinine),
                    color = AppColors.cotinine,
                    mg = levels.cotinineMg,
                    ngMl = cotNg,
                    until = nonUserAt,
                    untilRes = R.string.non_user_in,
                    reachedRes = R.string.non_user_reached,
                    nowMs = clock,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                stringResource(R.string.receptors_busy, Fmt.percent(Levels.receptorsBusy(nicNg))),
                style = MaterialTheme.typography.bodyMedium,
            )
            val rest = restMs
            Text(
                stringResource(
                    R.string.receptor_rest,
                    when {
                        rest == null -> "…"
                        rest < 60_000L -> stringResource(R.string.duration_m, 0)
                        else -> durationText(rest)
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.receptor_rest_hint, Fmt.ngMl(IntervalPlan.RECEPTOR_HALF_NG_ML)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenLevels, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.levels_link))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }

        SoftCard(padding = 16.dp) {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.no_data_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        val labels = listOf(R.string.range_8h, R.string.range_48h, R.string.range_7d)
                        labels.forEachIndexed { i, res ->
                            SegmentedButton(
                                selected = preset == i,
                                // At "now" the window keeps it in place; scrolled away, it zooms around the middle.
                                onClick = { scope.launch { view.zoomTo(PRESETS_H[i], aroundNow = !awayFromNow) } },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                            ) { Text(stringResource(res)) }
                        }
                    }
                    if (awayFromNow) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { scope.launch { view.goToNow() } },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                        ) { Text(stringResource(R.string.chart_now)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val r = reach
                if (r == null) {
                    Spacer(Modifier.height(272.dp))
                } else {
                    ScrollableBodyChart(
                        span = r.span,
                        view = view,
                        nicotineColor = AppColors.nicotine,
                        cotinineColor = AppColors.cotinine,
                        leftAxisTitle = stringResource(R.string.axis_nicotine),
                        rightAxisTitle = stringResource(R.string.axis_cotinine),
                        planLevelMg = planLevel,
                        nextOnPlanMs = nextOnPlan?.takeIf { it > clock },
                        planColor = AppColors.plan,
                        nowLabel = stringResource(R.string.time_now),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.chart_history_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.chart_forecast_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendSwatch(AppColors.nicotine, stringResource(R.string.nicotine))
                    LegendSwatch(AppColors.cotinine, stringResource(R.string.cotinine))
                    LegendSwatch(MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.legend_future), dashed = true)
                    if (planLevel != null) {
                        LegendSwatch(
                            AppColors.plan,
                            stringResource(
                                if (settings.intervalMode == IntervalMode.LOWER) R.string.chart_lower else R.string.chart_hold,
                                Fmt.oneDecimal(planLevel),
                            ),
                            dashed = true,
                        )
                    }
                }
            }
        }

        Text(stringResource(R.string.body_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(
                R.string.model_summary,
                Fmt.mg(pk.nicotineHalfLifeHours), Fmt.mg(pk.cotinineHalfLifeHours),
                (Pharmacokinetics.uptake(pk) * 100).roundToInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val CLEARED = Long.MIN_VALUE

/** The chart's points and how far it can be dragged: back to [earliestMs], ahead to [latestMs]. */
private class Reach(val earliestMs: Long, val latestMs: Long, val span: BodySpan)

/** One column of the summary card: how much is in now, the blood level, and when 95 % of it will be gone. */
@Composable
private fun SubstanceSummary(
    name: String,
    color: Color,
    mg: Double,
    ngMl: Double,
    /** When it gets down to the mark [untilRes] names; [CLEARED] when it is there already, null while being worked out. */
    until: Long?,
    untilRes: Int,
    reachedRes: Int,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color, size = 10)
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.mg_value, Fmt.oneDecimal(mg)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        val small = MaterialTheme.typography.bodySmall
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Text(stringResource(R.string.approx_ng_ml, Fmt.ngMl(ngMl)), style = small, color = muted)
        // Always two lines, so the card keeps its height whatever they say.
        val (first, second) = when {
            until == null -> "" to ""
            until == CLEARED || until <= nowMs -> stringResource(reachedRes) to ""
            else -> stringResource(untilRes, durationText(until - nowMs)) to
                stringResource(R.string.gone_by, Fmt.timeOrDayTime(until, nowMs))
        }
        Text(first, style = small, color = muted)
        Text(second, style = small, color = muted)
    }
}

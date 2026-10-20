package app.pouchwise.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
private val RANGES_MS = listOf(12L, 48L, 7L * 24).map { it * HOUR_MS }
/** Chart points every 6 min plus every pouch in and out: smooth at 12 h, light enough for 37 days. */
private const val SAMPLE_STEP_MS = 6 * 60_000L
private const val HISTORY_MAX_MS = 30L * 24 * HOUR_MS
private const val FORECAST_MAX_MS = 7L * 24 * HOUR_MS
/** Where "now" sits in a freshly opened window: two thirds past, one third forecast. */
private const val NOW_AT = 2.0 / 3.0

@Composable
fun BodyScreen(vm: AppViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val pk = settings.pk

    var rangeIdx by rememberSaveable { mutableIntStateOf(0) }
    val windowMs = RANGES_MS[rangeIdx]
    val defaultEndH = (windowMs * (1 - NOW_AT) / HOUR_MS).toFloat()
    val endOffsetH = remember(rangeIdx) { Animatable(defaultEndH) }
    val awayFromNow by remember(endOffsetH) {
        derivedStateOf { abs(endOffsetH.value - defaultEndH) > windowMs / HOUR_MS * 0.02f }
    }
    val scope = rememberCoroutineScope()

    // The ticker only moves every 30 s; a pouch logged since then is already in.
    val clock = remember(now, entries) { maxOf(now, System.currentTimeMillis()) }
    val doses = remember(entries, settings) { entries.map { PouchTiming.toDose(it, settings) } }
    val levels = remember(doses, pk, clock) { Pharmacokinetics.levelsAt(clock, doses, pk) }
    val mostlyGone by produceState<Clearance?>(initialValue = null, doses, pk, clock) {
        value = withContext(Dispatchers.Default) { Pharmacokinetics.mostlyGone(clock, doses, pk) }
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
            val cleared = Pharmacokinetics.clearance(clock, doses, pk)
            val clearEnd = max(cleared.nicotineClearMs ?: clock, cleared.cotinineClearMs ?: clock)
            val earliest = max(clock - HISTORY_MAX_MS, (firstMs ?: clock) - HOUR_MS)
            val latest = clock + (clearEnd - clock).coerceIn(2 * HOUR_MS, FORECAST_MAX_MS)
            val widest = RANGES_MS.last()
            val from = min(earliest, clock - (widest * NOW_AT).toLong())
            val to = max(latest, clock + (widest * (1 - NOW_AT)).toLong())
            val step = SAMPLE_STEP_MS
            Reach(
                earliest, latest,
                BodySpan(
                    nowMs = clock,
                    past = Pharmacokinetics.series(from, clock, step, doses, pk),
                    future = Pharmacokinetics.series(clock, to, step, doses, pk),
                ),
            )
        }
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
                    gone = mostlyGone?.let { it.nicotineClearMs ?: CLEARED },
                    nowMs = clock,
                    modifier = Modifier.weight(1f),
                )
                SubstanceSummary(
                    name = stringResource(R.string.cotinine),
                    color = AppColors.cotinine,
                    mg = levels.cotinineMg,
                    ngMl = cotNg,
                    gone = mostlyGone?.let { it.cotinineClearMs ?: CLEARED },
                    nowMs = clock,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
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
        }

        SoftCard(padding = 16.dp) {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.no_data_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        val labels = listOf(R.string.range_12h, R.string.range_48h, R.string.range_7d)
                        labels.forEachIndexed { i, res ->
                            SegmentedButton(
                                selected = rangeIdx == i,
                                onClick = { rangeIdx = i },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                            ) { Text(stringResource(res)) }
                        }
                    }
                    if (awayFromNow) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { scope.launch { endOffsetH.animateTo(defaultEndH) } },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                        ) { Text(stringResource(R.string.chart_now)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val r = reach
                if (r == null) {
                    Spacer(Modifier.height(272.dp))
                } else {
                    val at = r.span.nowMs
                    ScrollableBodyChart(
                        span = r.span,
                        startMs = min(at - (windowMs * NOW_AT).toLong(), r.earliestMs),
                        endMs = max(at + (windowMs * (1 - NOW_AT)).toLong(), r.latestMs),
                        windowMs = windowMs,
                        endOffsetH = endOffsetH,
                        nicotineColor = AppColors.nicotine,
                        cotinineColor = AppColors.cotinine,
                        leftAxisTitle = stringResource(R.string.axis_nicotine),
                        rightAxisTitle = stringResource(R.string.axis_cotinine),
                        planLevelMg = planLevel,
                        nextOnPlanMs = nextOnPlan?.takeIf { it > clock },
                        planColor = AppColors.plan,
                        planLabel = planLevel?.let {
                            stringResource(
                                if (settings.intervalMode == IntervalMode.LOWER) R.string.chart_lower else R.string.chart_hold,
                                Fmt.oneDecimal(it),
                            )
                        }.orEmpty(),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.chart_history_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.chart_forecast_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendSwatch(AppColors.nicotine, stringResource(R.string.nicotine))
                    LegendSwatch(AppColors.cotinine, stringResource(R.string.cotinine))
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendSwatch(MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.legend_past))
                    LegendSwatch(MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.legend_future), dashed = true)
                }
            }
        }

        Text(stringResource(R.string.body_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(
                R.string.model_summary,
                Fmt.mg(pk.nicotineHalfLifeHours), Fmt.mg(pk.cotinineHalfLifeHours),
                (pk.absorptionFraction * 100).roundToInt(), Pharmacokinetics.REFERENCE_MINUTES.toInt(),
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
    /** When 95 % will be gone; [CLEARED] when there is next to nothing, null while still being worked out. */
    gone: Long?,
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
            gone == null -> "" to ""
            gone == CLEARED || gone <= nowMs -> stringResource(R.string.cleared_short) to ""
            else -> stringResource(R.string.gone_95_short, durationText(gone - nowMs)) to
                stringResource(R.string.gone_by, Fmt.timeOrDayTime(gone, nowMs))
        }
        Text(first, style = small, color = muted)
        Text(second, style = small, color = muted)
    }
}

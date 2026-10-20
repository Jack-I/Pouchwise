package app.pouchwise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.model.*
import app.pouchwise.ui.components.*
import app.pouchwise.util.Fmt
import app.pouchwise.util.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val WEEKS = 5

@Composable
fun HistoryScreen(vm: AppViewModel, onEditPouches: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    // The ticker lags up to 30 s behind midnight; the wall clock does not.
    val today = DayKeys.epochDay(maxOf(now, System.currentTimeMillis()), settings.dayStartHour)
    val todayDow = LocalDate.ofEpochDay(today).dayOfWeek.value // 1 = Monday
    val gridEnd = today + (7 - todayDow)
    val gridStart = gridEnd - WEEKS * 7 + 1
    val chartStart = today - 13
    val firstDay = remember(entries, settings.dayStartHour) {
        entries.minOfOrNull { DayKeys.of(it, settings.dayStartHour) }
    } ?: today

    val stats = remember(entries, plans, settings, today) {
        Stats.dailyStats(entries, settings, minOf(firstDay, gridStart, chartStart, today - 30), today, plans, today)
    }
    val byDay = remember(stats) { stats.associateBy { it.epochDay } }
    val chartStats = remember(stats) { stats.filter { it.epochDay >= chartStart } }
    val futureLimits = remember(settings, today) { (1..7).map { (today + it) to Limits.limitForDayRounded(settings, today + it) } }

    // Finished days only: today is still running.
    val withData = stats.filter { it.hasData }
    val overTotal = withData.count { !it.withinLimit }
    val last30 = withData.filter { it.epochDay in (today - 30)..(today - 1) }
    val within30 = last30.count { it.withinLimit }
    val last7 = withData.filter { it.epochDay in (today - 7)..(today - 1) }
    // In today's unit from the raw counts, so a switch between pouches and mg does not mix units.
    val avg7 = if (last7.isEmpty()) 0.0
               else last7.sumOf { if (settings.limitMode == LimitMode.COUNT) it.count.toDouble() else it.mg } / last7.size
    val streak = Stats.streak(stats)
    val totalMg = entries.sumOf { it.strengthMg }

    var selectedDay by remember { mutableStateOf<Long?>(null) }
    var section by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.history_title))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val sections = listOf(R.string.history_days, R.string.history_patterns, R.string.history_spending)
            sections.forEachIndexed { i, res ->
                SegmentedButton(
                    selected = section == i,
                    onClick = { section = i },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = sections.size),
                    icon = {},
                ) { Text(stringResource(res), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) }
            }
        }

        when (section) {
            1 -> PatternsCard(remember(entries, settings, today) { Patterns.summary(entries, settings, today, System.currentTimeMillis()) })
            2 -> SpendingCard(remember(entries, settings, today) { Spending.summary(entries, settings, today) }, settings, onEditPouches)
            else -> {
            SoftCard {
                Text(stringResource(R.string.garden_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.garden_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                DayGrid(
                    gridStart = gridStart,
                    today = today,
                    byDay = byDay,
                    selectedDay = selectedDay,
                    onSelect = { selectedDay = if (selectedDay == it) null else it },
                )
                val sel = selectedDay?.let { byDay[it] }
                Spacer(Modifier.height(10.dp))
                if (sel != null) {
                    val absorbed = entries
                        .filter { DayKeys.of(it, settings.dayStartHour) == sel.epochDay }
                        .sumOf { PouchTiming.absorbedMg(it, settings, now) }
                    Text(
                        "${Fmt.dateWithWeekday(sel.epochDay)} · ${pluralStringResource(R.plurals.count_pouches, sel.count, sel.count)} · " +
                            "${Fmt.mg(sel.mg)} ${stringResource(R.string.unit_mg)} · " +
                            "${stringResource(R.string.absorbed_short, Fmt.oneDecimal(absorbed))} · " +
                            "${stringResource(R.string.limit_word)}: ${limitAmount(sel.mode, sel.limit.toDouble())}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ColourLegend()
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.stats_streak), streak.toString(), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.stats_over_total), overTotal.toString(), Modifier.weight(1f),
                    accent = if (overTotal > 0) MaterialTheme.colorScheme.error else null,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.stats_within_30), "$within30 / ${last30.size}", Modifier.weight(1f))
                StatTile(
                    if (settings.limitMode == LimitMode.COUNT) stringResource(R.string.stats_avg7_count) else stringResource(R.string.stats_avg7_mg),
                    Fmt.oneDecimal(avg7),
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.stats_total), entries.size.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_total_mg), Fmt.mg(totalMg), Modifier.weight(1f))
            }

            SoftCard {
                Text(stringResource(R.string.daily_usage), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendSwatch(MaterialTheme.colorScheme.primary, stringResource(R.string.legend_usage))
                    LegendSwatch(MaterialTheme.colorScheme.onSurface, stringResource(R.string.legend_limit))
                }
                Text(
                    if (settings.limitMode == LimitMode.COUNT) "${stringResource(R.string.unit_pouches)} / ${stringResource(R.string.per_day)}"
                    else "${stringResource(R.string.unit_mg)} / ${stringResource(R.string.per_day)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                DailyBarChart(
                    stats = chartStats,
                    futureLimits = futureLimits,
                    todayDay = today,
                    mode = settings.limitMode,
                    barColor = MaterialTheme.colorScheme.primary,
                    lineColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            }
        }
    }
}

@Composable
private fun SpendingCard(summary: SpendingSummary, settings: Settings, onEditPouches: () -> Unit) {
    val currency = Money.symbol(settings)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SoftCard {
        Text(stringResource(R.string.spending_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val perPouch = summary.perPouch
        if (perPouch == null) {
            Text(stringResource(R.string.spending_no_prices), style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEditPouches) { Text(stringResource(R.string.spending_set_prices)) }
            return@SoftCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniTile(stringResource(R.string.spending_week), Fmt.money(summary.week, currency), Modifier.weight(1f))
            MiniTile(stringResource(R.string.spending_month), Fmt.money(summary.month, currency), Modifier.weight(1f))
            if (summary.saved != null && summary.sinceDay != null) {
                MiniTile(
                    stringResource(R.string.spending_saved, Fmt.dateShort(summary.sinceDay)),
                    "≈ " + Fmt.money(summary.saved, currency),
                    Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val baseline = settings.baselinePerDay
        Text(
            if (summary.saved != null && baseline != null) {
                stringResource(R.string.spending_basis, pluralStringResource(R.plurals.count_pouches, baseline, baseline), Fmt.money(perPouch, currency))
            } else stringResource(R.string.spending_no_baseline),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        if (summary.unpriced) Text(stringResource(R.string.spending_unpriced), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun PatternsCard(p: PatternSummary) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.bodySmall
    SoftCard {
        Text(stringResource(R.string.patterns_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (p.byHour.sum() == 0) {
            Text(stringResource(R.string.patterns_empty), style = small, color = muted)
            return@SoftCard
        }
        Text(stringResource(R.string.patterns_by_hour), style = small, color = muted)
        SimpleBars(p.byHour.map { it.toDouble() }, (0..23).map { it.toString() }, MaterialTheme.colorScheme.primary, labelEvery = 3)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.patterns_by_weekday), style = small, color = muted)
        val locale = Locale.getDefault()
        SimpleBars(p.byWeekday, DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale) }, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.patterns_first), style = small, color = muted)
                Text(p.firstNow?.let { Fmt.clockMinutes(it) } ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                p.firstShift?.takeIf { it != 0 }?.let { shift ->
                    Text(
                        if (shift > 0) stringResource(R.string.patterns_later, durationText(shift * 60_000L))
                        else stringResource(R.string.patterns_earlier, durationText(-shift * 60_000L)),
                        style = small,
                        color = if (shift > 0) MaterialTheme.colorScheme.secondary else muted,
                    )
                }
            }
            Sparkline(p.firstPouch.map { it.toDouble() }, MaterialTheme.colorScheme.secondary, Modifier.size(width = 120.dp, height = 44.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniTile(stringResource(R.string.patterns_gap), p.typicalGapMs?.let { durationText(it) } ?: "—", Modifier.weight(1f))
            MiniTile(stringResource(R.string.patterns_in_mouth), p.avgInMouthMs?.let { durationText(it) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniTile(label: String, value: String, modifier: Modifier = Modifier, accent: androidx.compose.ui.graphics.Color? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DayGrid(
    gridStart: Long,
    today: Long,
    byDay: Map<Long, DayStat>,
    selectedDay: Long?,
    onSelect: (Long) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val locale = Locale.getDefault()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Spacer(Modifier.width(44.dp))
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (week in 0 until WEEKS) {
            val rowStart = gridStart + week * 7
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    Fmt.dateShort(rowStart),
                    modifier = Modifier.width(40.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (i in 0 until 7) {
                    val day = rowStart + i
                    val stat = if (day <= today) byDay[day] else null
                    val level = GardenLevel.of(stat)
                    val isToday = day == today
                    val selected = day == selectedDay
                    val showNumbers = stat != null && stat.hasData
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (level == GardenLevel.EMPTY) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (day > today) 0.35f else 0.8f)
                                else gardenColor(level, dark),
                            )
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp))
                                else if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                else Modifier,
                            )
                            .then(if (day <= today) Modifier.clickable { onSelect(day) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showNumbers) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stat!!.count.toString(),
                                    fontSize = 14.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    Fmt.mg(stat.mg),
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColourLegend() {
    val dark = isSystemInDarkTheme()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GardenLevel.entries.filter { it != GardenLevel.EMPTY }.forEach { lvl ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(gardenColor(lvl, dark)))
                Spacer(Modifier.width(4.dp))
                Text(lvl.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

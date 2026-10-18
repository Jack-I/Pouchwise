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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val WEEKS = 5

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    val today = DayKeys.epochDay(now, settings.dayStartHour)
    val todayDow = LocalDate.ofEpochDay(today).dayOfWeek.value // 1 = Monday
    val gridEnd = today + (7 - todayDow)
    val gridStart = gridEnd - WEEKS * 7 + 1
    val chartStart = today - 13

    val stats = remember(entries, settings, today) {
        Stats.dailyStats(entries, settings, minOf(gridStart, chartStart, today - 29), today)
    }
    val byDay = remember(stats) { stats.associateBy { it.epochDay } }
    val chartStats = remember(stats) { stats.filter { it.epochDay >= chartStart } }
    val futureLimits = remember(settings, today) { (1..7).map { (today + it) to Limits.limitForDayRounded(settings, today + it) } }

    val last30 = stats.filter { it.epochDay in (today - 29)..(today - 1) && it.hasData }
    val within30 = last30.count { it.withinLimit }
    val last7 = stats.filter { it.epochDay in (today - 6)..today && it.hasData }
    val avg7 = if (last7.isEmpty()) 0.0 else last7.sumOf { it.used } / last7.size
    val streak = Stats.streak(stats)

    var selectedDay by remember { mutableStateOf<Long?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.history_title))

        SoftCard {
            Text(stringResource(R.string.garden_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.garden_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            GardenGrid(
                gridStart = gridStart,
                today = today,
                byDay = byDay,
                selectedDay = selectedDay,
                onSelect = { selectedDay = if (selectedDay == it) null else it },
            )
            val sel = selectedDay?.let { byDay[it] }
            Spacer(Modifier.height(10.dp))
            if (sel != null) {
                val unit = if (sel.mode == LimitMode.COUNT) stringResource(R.string.unit_pouches) else stringResource(R.string.unit_mg)
                Text(
                    "${Fmt.dateWithWeekday(sel.epochDay)} · ${sel.count} ${stringResource(R.string.unit_pouches)} · " +
                        "${Fmt.mg(sel.mg)} ${stringResource(R.string.unit_mg)} · ${stringResource(R.string.limit_today).lowercase()}: ${sel.limit} $unit",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                GardenLegend()
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stringResource(R.string.stats_streak), streak.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.stats_within_30), "$within30 / ${last30.size}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                if (settings.limitMode == LimitMode.COUNT) stringResource(R.string.stats_avg7_count) else stringResource(R.string.stats_avg7_mg),
                Fmt.oneDecimal(avg7),
                Modifier.weight(1f),
            )
            StatTile(stringResource(R.string.stats_total), entries.size.toString(), Modifier.weight(1f))
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
                barColor = MaterialTheme.colorScheme.primary,
                lineColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun GardenGrid(
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
                        if (level != GardenLevel.EMPTY) Text(level.emoji, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GardenLegend() {
    val dark = isSystemInDarkTheme()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            GardenLevel.BLOOM to "0",
            GardenLevel.TREE to "≤50%",
            GardenLevel.HERB to "≤100%",
            GardenLevel.LEAVES to "≤150%",
            GardenLevel.STUMP to ">150%",
        ).forEach { (lvl, txt) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(gardenColor(lvl, dark)), contentAlignment = Alignment.Center) {
                    Text(lvl.emoji, fontSize = 11.sp)
                }
                Spacer(Modifier.width(3.dp))
                Text(txt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

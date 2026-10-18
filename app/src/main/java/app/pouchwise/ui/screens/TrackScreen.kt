package app.pouchwise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.model.*
import app.pouchwise.ui.components.*
import app.pouchwise.ui.theme.pouchColor
import app.pouchwise.util.Fmt
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
fun TrackScreen(vm: AppViewModel, onEditPouches: () -> Unit) {
    val typesOrNull by vm.types.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    val types = typesOrNull ?: return
    val active = remember(types) { types.filter { !it.archived } }
    val typesById = remember(types) { types.associateBy { it.id } }

    // A one-second clock runs only while a pouch is in, for the countdown.
    val anyActive = entries.any { PouchTiming.isActive(it, settings, now) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(anyActive) {
        while (anyActive) {
            tick = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val activeEntries = remember(entries, settings, tick) {
        entries.filter { PouchTiming.isActive(it, settings, tick) }
    }

    val today = DayKeys.epochDay(now, settings.dayStartHour)
    val dayStart = DayKeys.startOfDayMs(today, settings.dayStartHour)
    val dayEnd = DayKeys.endOfDayMs(today, settings.dayStartHour)
    val todaysAsc = remember(entries, dayStart, dayEnd) {
        entries.filter { it.timestamp >= dayStart && it.timestamp < dayEnd }
    }
    val todays = remember(todaysAsc) { todaysAsc.asReversed() }
    val count = todaysAsc.size
    val mg = todaysAsc.sumOf { it.strengthMg }
    val limit = Limits.limitForDayRounded(settings, today)
    val overIds = remember(todaysAsc, settings, limit) { Limits.overLimitEntryIds(todaysAsc, settings, limit) }
    val lastEntry = entries.lastOrNull()

    var timePickFor by remember { mutableStateOf<PouchType?>(null) }
    var editEntry by remember { mutableStateOf<LogEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<LogEntry?>(null) }
    var addDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TodayCard(settings = settings, count = count, mg = mg, limit = limit, lastEntryMs = lastEntry?.timestamp, nowMs = now)
        }

        items(activeEntries, key = { "active-" + it.id }) { e ->
            ActivePouchCard(
                entry = e,
                name = typesById[e.pouchTypeId]?.name ?: "?",
                colorIndex = typesById[e.pouchTypeId]?.colorIndex ?: 0,
                nowMs = tick,
                autoMs = PouchTiming.autoMs(settings),
                onRemove = { vm.finishPouch(e) },
            )
        }

        if (active.isEmpty()) {
            item {
                SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(stringResource(R.string.no_types_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.no_types_body, settings.pk.absorptionMinutes), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { addDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_pouch_type))
                    }
                }
            }
        } else {
            item {
                SectionTitle(stringResource(R.string.your_pouches), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { addDialog = true }) { Text(stringResource(R.string.add_pouch_type)) }
                }
            }
            items(active, key = { "type-${it.id}" }) { type ->
                val fits = Limits.remainingPouches(settings, limit, count, mg, type.strengthMg)
                PouchTypeRow(
                    type = type,
                    fits = fits,
                    onLog = { vm.logPouch(type, System.currentTimeMillis()) },
                    onLogAtTime = { timePickFor = type },
                )
            }
            item {
                OutlinedButton(onClick = onEditPouches, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.edit_pouches), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        item { SectionTitle(stringResource(R.string.todays_log), modifier = Modifier.padding(top = 8.dp)) }
        if (todays.isEmpty()) {
            item { Text(stringResource(R.string.empty_log), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(todays, key = { "entry-${it.id}" }) { e ->
                LogRow(
                    entry = e,
                    name = typesById[e.pouchTypeId]?.name ?: "?",
                    colorIndex = typesById[e.pouchTypeId]?.colorIndex ?: 0,
                    durationLabel = if (PouchTiming.isActive(e, settings, tick)) stringResource(R.string.in_mouth)
                                    else stringResource(R.string.duration_m, (PouchTiming.effectiveDurationMs(e, settings) / 60_000L).toInt()),
                    over = e.id in overIds,
                    onEditTime = { editEntry = e },
                    onDelete = { deleteEntry = e },
                )
            }
        }
    }

    timePickFor?.let { type ->
        TimeDialog(
            title = stringResource(R.string.pick_time),
            initialMs = now,
            onDismiss = { timePickFor = null },
            onConfirm = { h, m ->
                val at = timeTodayAt(h, m, now)
                timePickFor = null
                vm.logPouch(type, at)
            },
        )
    }

    editEntry?.let { e ->
        TimeDialog(
            title = stringResource(R.string.edit_time),
            initialMs = e.timestamp,
            onDismiss = { editEntry = null },
            onConfirm = { h, m ->
                val zone = ZoneId.systemDefault()
                val date = Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate()
                val at = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
                vm.moveEntry(e, at)
                editEntry = null
            },
        )
    }

    deleteEntry?.let { e ->
        ConfirmDialog(
            title = stringResource(R.string.delete_entry_q),
            text = null,
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleteEntry = null },
            onConfirm = { vm.deleteEntry(e); deleteEntry = null },
            destructive = true,
        )
    }

    if (addDialog) {
        PouchTypeDialog(
            initial = null,
            onDismiss = { addDialog = false },
            onSave = { name, strength, color, speed -> vm.addType(name, strength, color, speed); addDialog = false },
        )
    }
}

/** Today's date at [hour]:[minute]; if that is in the future, assume yesterday was meant. */
private fun timeTodayAt(hour: Int, minute: Int, nowMs: Long): Long {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    var at = today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    if (at > nowMs + 60_000L) at -= 24L * 3_600_000L
    return at
}

private fun mmss(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
}

@Composable
private fun TodayCard(settings: Settings, count: Int, mg: Double, limit: Int, lastEntryMs: Long?, nowMs: Long) {
    val mode = settings.limitMode
    val used = Limits.usage(mode, count, mg)
    val over = used > limit + 1e-9
    val ratio = if (limit > 0) used / limit else if (used > 0) 2.0 else 0.0
    val level = GardenLevel.of(DayStat(0, count, mg, limit, mode, hasData = true))

    val headline = when (mode) {
        LimitMode.COUNT -> {
            val left = limit - count
            when {
                left > 0 -> pluralStringResource(R.plurals.pouches_left_today, left, left)
                left == 0 -> stringResource(R.string.limit_reached)
                else -> pluralStringResource(R.plurals.over_limit_by_pouches, -left, -left)
            }
        }
        LimitMode.MG -> {
            val left = limit - mg
            when {
                left > 0.05 -> stringResource(R.string.mg_left_today, Fmt.mg(left))
                left > -0.05 -> stringResource(R.string.limit_reached)
                else -> stringResource(R.string.over_limit_by_mg, Fmt.mg(-left))
            }
        }
    }
    val detail = when (mode) {
        LimitMode.COUNT -> stringResource(R.string.used_of_limit_count, count, limit, Fmt.mg(mg))
        LimitMode.MG -> stringResource(R.string.used_of_limit_mg, Fmt.mg(mg), limit.toString(), count)
    }
    val container = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val barColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val headlineColor = if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface

    SoftCard(containerColor = container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (over) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            } else {
                Text("🫙", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = headlineColor)
                Spacer(Modifier.height(6.dp))
                Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            BigEmoji(level.emoji.ifEmpty { "🌱" }, size = 60)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.weight(1f).height(10.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surface,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.width(12.dp))
            Text(Fmt.percent(ratio), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (lastEntryMs != null) stringResource(R.string.last_pouch_ago, durationText(nowMs - lastEntryMs))
                   else stringResource(R.string.no_pouches_today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivePouchCard(entry: LogEntry, name: String, colorIndex: Int, nowMs: Long, autoMs: Long, onRemove: () -> Unit) {
    val elapsed = (nowMs - entry.timestamp).coerceIn(0L, autoMs)
    val remaining = autoMs - elapsed
    SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.in_mouth), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(pouchColor(colorIndex))
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mg_value, Fmt.mg(entry.strengthMg)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.auto_remove_in, mmss(remaining)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(mmss(elapsed), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { (elapsed.toFloat() / autoMs.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface,
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRemove, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.remove_pouch), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PouchTypeRow(type: PouchType, fits: Int, onLog: () -> Unit, onLogAtTime: () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(pouchColor(type.colorIndex))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(type.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mg_value, Fmt.mg(type.strengthMg)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val speed = ReleaseSpeed.fromInt(type.releaseSpeed)
                    if (speed != ReleaseSpeed.NORMAL) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (speed == ReleaseSpeed.FAST) R.string.release_fast else R.string.release_slow).lowercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (fits > 0) pluralStringResource(R.plurals.fits_today, fits, fits) else stringResource(R.string.none_fit_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fits > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(onClick = onLog, onLongClick = onLogAtTime),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.log_one), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun LogRow(
    entry: LogEntry,
    name: String,
    colorIndex: Int,
    durationLabel: String,
    over: Boolean,
    onEditTime: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (over) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Fmt.time(entry.timestamp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = accent, modifier = Modifier.widthIn(min = 52.dp))
        Spacer(Modifier.width(8.dp))
        ColorDot(pouchColor(colorIndex), size = 10)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = accent)
            Text(
                "${stringResource(R.string.mg_value, Fmt.mg(entry.strengthMg))} · $durationLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (over) {
            Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.over_limit_marker), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onEditTime) { Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.edit_time)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
    }
}

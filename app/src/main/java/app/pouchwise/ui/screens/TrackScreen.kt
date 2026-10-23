package app.pouchwise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.data.pricePerPouch
import app.pouchwise.model.*
import app.pouchwise.ui.components.*
import app.pouchwise.ui.theme.AppColors
import app.pouchwise.ui.theme.pouchColor
import app.pouchwise.util.Fmt
import app.pouchwise.util.Money
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun TrackScreen(vm: AppViewModel, onEditPouches: () -> Unit) {
    val typesOrNull by vm.types.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val feltMarks by vm.feltMarks.collectAsStateWithLifecycle()

    val types = typesOrNull ?: return
    val active = remember(types) { types.filter { !it.archived } }
    val typesById = remember(types) { types.associateBy { it.id } }

    // A one-second clock runs only while a pouch is in, for the countdown and the running absorbed amount.
    // The wall clock is read on every recomposition so a pouch just logged shows at once, not at the next 30-s tick.
    val wallNow = maxOf(now, System.currentTimeMillis())
    val anyActive = entries.any { PouchTiming.isActive(it, settings, wallNow) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(anyActive) {
        while (anyActive) {
            tick = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val clock = if (anyActive) maxOf(tick, wallNow) else wallNow
    val activeEntries = remember(entries, settings, clock) {
        entries.filter { PouchTiming.isActive(it, settings, clock) }
    }

    val today = DayKeys.epochDay(wallNow, settings.dayStartHour)
    val todaysAsc = remember(entries, settings.dayStartHour, today) {
        entries.filter { DayKeys.of(it, settings.dayStartHour) == today }
    }
    val todays = remember(todaysAsc) { todaysAsc.asReversed() }
    val count = todaysAsc.size
    val mg = todaysAsc.sumOf { it.strengthMg }
    val absorbedToday = todaysAsc.sumOf { PouchTiming.absorbedMg(it, settings, clock) }
    val limit = Limits.limitForDayRounded(settings, today)
    val overIds = remember(todaysAsc, settings, limit) { Limits.overLimitEntryIds(todaysAsc, settings, limit) }
    val pouchInNow = activeEntries.isNotEmpty()
    // The interval plan: the level below which the next pouch is due, and when the forecast gets there.
    val doses = remember(entries, settings) { entries.map { PouchTiming.toDose(it, settings) } }
    val planLevel = remember(entries, settings, today) {
        val since = wallNow - IntervalPlan.DATA_MS
        IntervalPlan.threshold(entries.filter { it.timestamp >= since }, settings, wallNow)
    }
    val nextOnPlan = remember(doses, settings, planLevel, now) {
        planLevel?.let { IntervalPlan.nextOnPlanMs(doses, settings.pk, it, System.currentTimeMillis()) }
    }
    val earlyIds = remember(todaysAsc, entries, settings) { IntervalPlan.earlyIds(todaysAsc, entries, settings) }
    val spentToday = todaysAsc.mapNotNull { it.pricePerPouch }.takeIf { it.isNotEmpty() }?.sum()
    // "Last pouch" counts from the moment the most recent one came out, by hand or by its own time.
    val lastOutMs = PouchTiming.lastOutMs(entries, settings, clock)

    var timePickFor by remember { mutableStateOf<PouchType?>(null) }
    var editEntry by remember { mutableStateOf<LogEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<LogEntry?>(null) }
    var addDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TodayCard(
                settings = settings, count = count, mg = mg, absorbedMg = absorbedToday, limit = limit,
                lastOutMs = lastOutMs, pouchInNow = pouchInNow, nowMs = clock,
                money = spentToday?.let { Fmt.money(it, Money.symbol(settings)) },
                plan = if (settings.intervalMode == IntervalMode.OFF) null else PlanInfo(
                    settings.intervalMode, planLevel, nextOnPlan,
                    noneFit = Limits.noneFit(settings, limit, count, mg, active.map { it.strengthMg }),
                ),
            )
        }

        items(activeEntries, key = { "active-" + it.id }) { e ->
            ActivePouchCard(
                entry = e,
                name = typesById[e.pouchTypeId]?.name ?: "?",
                colorIndex = typesById[e.pouchTypeId]?.colorIndex ?: 0,
                nowMs = tick,
                autoMs = PouchTiming.autoMs(e, settings),
                absorbedMg = PouchTiming.absorbedMg(e, settings, tick),
                onRemove = { vm.finishPouch(e) },
                marks = feltMarks[e.id].orEmpty(),
                // Tapping the latest answer again takes it back, for a wrong tap.
                onFelt = { f -> if (f.level == feltMarks[e.id]?.lastOrNull()?.level) vm.takeBackFelt(e) else vm.addFelt(e, f) },
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
                SectionTitle(stringResource(R.string.your_pouches), style = MaterialTheme.typography.titleMedium) {
                    TextButton(onClick = onEditPouches) { Text(stringResource(R.string.edit_short)) }
                    TextButton(onClick = { addDialog = true }) { Text(stringResource(R.string.add_pouch_type)) }
                }
            }
            items(active, key = { "type-${it.id}" }) { type ->
                val fits = Limits.remainingPouches(settings, limit, count, mg, type.strengthMg)
                PouchTypeRow(
                    type = type,
                    price = type.pricePerPouch()?.let { Fmt.money(it, Money.symbol(settings)) },
                    fits = fits,
                    onLog = { vm.logPouch(type, System.currentTimeMillis()) },
                    onLogAtTime = { timePickFor = type },
                )
            }
        }

        item {
            SectionTitle(
                stringResource(R.string.todays_log),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (todays.isEmpty()) {
            item { Text(stringResource(R.string.empty_log), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(todays, key = { "entry-${it.id}" }) { e ->
                val isActive = PouchTiming.isActive(e, settings, clock)
                LogRow(
                    entry = e,
                    name = typesById[e.pouchTypeId]?.name ?: "?",
                    colorIndex = typesById[e.pouchTypeId]?.colorIndex ?: 0,
                    durationLabel = if (isActive) stringResource(R.string.in_mouth)
                                    else stringResource(R.string.duration_m, (PouchTiming.effectiveDurationMs(e, settings) / 60_000L).toInt()),
                    absorbedLabel = stringResource(R.string.absorbed_short, Fmt.oneDecimal(PouchTiming.absorbedMg(e, settings, clock))),
                    over = e.id in overIds,
                    early = e.id in earlyIds,
                    marks = feltMarks[e.id].orEmpty(),
                    onEdit = { editEntry = e },
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
                val nowMs = System.currentTimeMillis()
                val todayDate = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
                timePickFor = null
                vm.logPouch(type, DayKeys.pastTimeMs(todayDate, h, m, nowMs))
            },
        )
    }

    editEntry?.let { e ->
        val type = typesById[e.pouchTypeId]
        val stillIn = remember(e.id) { PouchTiming.isActive(e, settings, System.currentTimeMillis()) }
        EntryDialog(
            title = "${type?.name ?: "?"} · ${stringResource(R.string.mg_value, Fmt.mg(e.strengthMg))}",
            color = pouchColor(type?.colorIndex ?: 0),
            startMs = e.timestamp,
            durationMs = if (stillIn) null else PouchTiming.effectiveDurationMs(e, settings),
            marks = feltMarks[e.id].orEmpty(),
            onDismiss = { editEntry = null },
            onSave = { start, duration, marks ->
                vm.editEntry(e, start, duration, marks)
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
            currency = Money.symbol(settings),
            onSave = { vm.addType(it); addDialog = false },
        )
    }
}

@Composable
private fun TodayCard(
    settings: Settings,
    count: Int,
    mg: Double,
    absorbedMg: Double,
    limit: Int,
    lastOutMs: Long?,
    pouchInNow: Boolean,
    nowMs: Long,
    money: String?,
    plan: PlanInfo?,
) {
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
        LimitMode.COUNT -> pluralStringResource(R.plurals.used_of_limit_count, limit, count, limit, Fmt.mg(mg))
        LimitMode.MG -> pluralStringResource(R.plurals.used_of_limit_mg, count, Fmt.mg(mg), limit.toString(), count)
    }
    val container = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val barColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val headlineColor = if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface

    SoftCard(containerColor = container, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = headlineColor)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.absorbed_today, Fmt.oneDecimal(absorbedMg)) + (money?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            BigEmoji(level.emoji.ifEmpty { "🌱" }, size = 40)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.weight(1f).height(8.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surface,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.width(10.dp))
            Text(Fmt.percent(ratio), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // While a pouch is in, its own card right below says so.
        if (!pouchInNow) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    lastOutMs == null -> stringResource(R.string.no_pouches_today)
                    nowMs - lastOutMs < 60_000L -> stringResource(R.string.last_pouch_just_now)
                    else -> stringResource(R.string.last_pouch_ago, durationText(nowMs - lastOutMs))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (plan != null) {
            Spacer(Modifier.height(10.dp))
            PlanRow(plan, nowMs)
        }
    }
}

/**
 * What the interval plan says now: its mode, its level (null while it is still learning), when the next is due,
 * and whether the day's limit leaves room for none of the pouches.
 */
private class PlanInfo(val mode: IntervalMode, val levelMg: Double?, val nextMs: Long?, val noneFit: Boolean)

@Composable
private fun PlanRow(plan: PlanInfo, nowMs: Long) {
    val level = plan.levelMg
    val waiting = level != null && plan.nextMs != null && plan.nextMs > nowMs
    val lower = plan.mode == IntervalMode.LOWER
    val title = when {
        plan.noneFit -> stringResource(R.string.plan_next_tomorrow)
        level == null -> stringResource(R.string.plan_learning)
        waiting -> stringResource(R.string.plan_next_at, Fmt.time(plan.nextMs!!))
        else -> stringResource(R.string.plan_now)
    }
    val detail = when {
        plan.noneFit -> null
        level == null -> stringResource(R.string.plan_learning_hint)
        waiting -> stringResource(
            if (lower) R.string.plan_wait_lower else R.string.plan_wait_hold,
            durationText(plan.nextMs!! - nowMs), Fmt.oneDecimal(level),
        )
        else -> stringResource(if (lower) R.string.plan_kept_lower else R.string.plan_kept_hold, Fmt.oneDecimal(level))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.plan.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = AppColors.plan)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActivePouchCard(
    entry: LogEntry,
    name: String,
    colorIndex: Int,
    nowMs: Long,
    autoMs: Long,
    absorbedMg: Double,
    onRemove: () -> Unit,
    marks: List<FeltMark>,
    onFelt: (Felt) -> Unit,
) {
    val elapsed = (nowMs - entry.timestamp).coerceIn(0L, autoMs)
    val remaining = autoMs - elapsed
    SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer, padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(pouchColor(colorIndex))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.mg_value, Fmt.mg(entry.strengthMg)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    stringResource(R.string.auto_remove_in, Fmt.mmss(remaining)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.absorbed_short, Fmt.oneDecimal(absorbedMg)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (elapsed.toFloat() / autoMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface,
                    strokeCap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.mmss(elapsed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onRemove,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_widget_pull_out), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.remove_pouch), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FeltChips(Felt.of(marks.lastOrNull()?.level), onFelt)
        if (marks.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(feltHistory(marks, entry.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PouchTypeRow(type: PouchType, price: String?, fits: Int, onLog: () -> Unit, onLogAtTime: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(pouchColor(type.colorIndex))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        type.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.mg_value, Fmt.mg(type.strengthMg)) + (price?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Text(
                    text = if (fits > 0) pluralStringResource(R.plurals.fits_today, fits, fits) else stringResource(R.string.none_fit_today),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fits > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(onClick = onLog, onLongClick = onLogAtTime),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.log_one), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
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
    absorbedLabel: String,
    over: Boolean,
    early: Boolean,
    marks: List<FeltMark>,
    onEdit: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, color = accent, modifier = Modifier.weight(1f, fill = false))
                if (early) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.early_marker),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.plan.copy(alpha = 0.3f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (marks.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        feltChain(marks),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                "${stringResource(R.string.mg_value, Fmt.mg(entry.strengthMg))} · $durationLabel · $absorbedLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (over) {
            Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.over_limit_marker), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_entry)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
    }
}

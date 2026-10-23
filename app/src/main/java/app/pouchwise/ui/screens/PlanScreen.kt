package app.pouchwise.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.data.LogEntry
import app.pouchwise.model.DayKeys
import app.pouchwise.model.IntervalMode
import app.pouchwise.model.IntervalPlan
import app.pouchwise.model.Pharmacokinetics
import app.pouchwise.model.LimitMode
import app.pouchwise.model.Limits
import app.pouchwise.model.Settings
import app.pouchwise.ui.components.*
import app.pouchwise.util.Fmt
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PlanScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    // Typical strength for converting between pouches and mg: the average of the visible pouches.
    val typicalStrength = types.orEmpty().filter { !it.archived }.map { it.strengthMg }.average().let { if (it.isNaN()) 8.0 else it }
    // The ticker lags up to 30 s behind midnight; the wall clock does not.
    val today = DayKeys.epochDay(maxOf(now, System.currentTimeMillis()), settings.dayStartHour)
    val mode = settings.limitMode
    val unit = if (mode == LimitMode.COUNT) stringResource(R.string.unit_pouches) else stringResource(R.string.unit_mg)
    val limitToday = Limits.limitForDayRounded(settings, today)
    val stepValue = if (mode == LimitMode.COUNT) 1.0 else 5.0

    var editing by remember { mutableStateOf<LimitEdit?>(null) }
    var pickStartDate by remember { mutableStateOf(false) }
    var pickTargetDate by remember { mutableStateOf(false) }

    fun setBase(v: Double) = vm.updateSettings { s ->
        val clamped = max(0.0, v)
        if (s.limitMode == LimitMode.COUNT) s.copy(dailyLimitCount = clamped.roundToInt()) else s.copy(dailyLimitMg = Math.round(clamped).toDouble())
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.plan_title))

        // headline card
        SoftCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val onSec = MaterialTheme.colorScheme.onSecondaryContainer
                    if (settings.taperEnabled && settings.taperTargetDay > today) {
                        val days = (settings.taperTargetDay - today).toInt()
                        Text(stringResource(R.string.responsible_usage), style = MaterialTheme.typography.labelLarge, color = onSec, letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing)
                        Text("$days", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = onSec)
                        Text(pluralStringResource(R.plurals.days_until_target, days, days).substringAfter(' '), style = MaterialTheme.typography.bodyLarge, color = onSec)
                    } else if (settings.taperEnabled) {
                        Text(stringResource(R.string.responsible_usage), style = MaterialTheme.typography.labelLarge, color = onSec)
                        Text("$limitToday", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = onSec)
                        Text("${limitUnit(mode, limitToday)} ${stringResource(R.string.per_day)} · ${stringResource(R.string.target_reached)}", style = MaterialTheme.typography.bodyLarge, color = onSec)
                    } else {
                        Text(stringResource(R.string.daily_limit_caption), style = MaterialTheme.typography.labelLarge, color = onSec)
                        Text("$limitToday", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = onSec)
                        Text("${limitUnit(mode, limitToday)} ${stringResource(R.string.per_day)}", style = MaterialTheme.typography.bodyLarge, color = onSec)
                    }
                }
                BigEmoji("🌱", size = 64)
            }
        }

        // limit mode
        SoftCard {
            Text(stringResource(R.string.limit_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == LimitMode.COUNT,
                    onClick = { vm.updateSettings { Limits.convertMode(it, LimitMode.COUNT, typicalStrength) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.mode_count)) }
                SegmentedButton(
                    selected = mode == LimitMode.MG,
                    onClick = { vm.updateSettings { Limits.convertMode(it, LimitMode.MG, typicalStrength) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.mode_mg)) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.mode_switch_hint, Fmt.mg(typicalStrength)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // fixed limit
        SoftCard {
            if (settings.taperEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.limit_today), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(limitAmount(mode, limitToday.toDouble()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                val base = Limits.baseLimit(settings)
                StepperRow(
                    label = stringResource(R.string.daily_limit),
                    valueText = limitAmount(mode, base),
                    onMinus = { setBase(base - stepValue) },
                    onPlus = { setBase(base + stepValue) },
                    onClickValue = { editing = LimitEdit.BASE },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.warn_over_limit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // taper
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.taper_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.taper_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.taperEnabled,
                    onCheckedChange = { on ->
                        vm.updateSettings { s ->
                            if (!on) s.copy(taperEnabled = false)
                            else {
                                val fresh = s.taperTargetDay <= s.taperStartDay || s.taperTargetDay < today
                                if (fresh) {
                                    val start = Limits.baseLimit(s)
                                    s.copy(
                                        taperEnabled = true,
                                        taperStartDay = today,
                                        taperStartValue = start,
                                        taperTargetDay = today + 60,
                                        taperTargetValue = max(if (s.limitMode == LimitMode.COUNT) 1.0 else 5.0, Math.round(start / 2.0).toDouble()),
                                    )
                                } else s.copy(taperEnabled = true)
                            }
                        }
                    },
                )
            }
            if (settings.taperEnabled) {
                Spacer(Modifier.height(12.dp))
                StepperRow(
                    label = stringResource(R.string.start_value),
                    valueText = limitAmount(mode, settings.taperStartValue),
                    onMinus = { vm.updateSettings { it.copy(taperStartValue = max(0.0, it.taperStartValue - stepValue)) } },
                    onPlus = { vm.updateSettings { it.copy(taperStartValue = it.taperStartValue + stepValue) } },
                    onClickValue = { editing = LimitEdit.START },
                )
                DateRow(stringResource(R.string.start_date), Fmt.dateLong(settings.taperStartDay)) { pickStartDate = true }
                Spacer(Modifier.height(8.dp))
                StepperRow(
                    label = stringResource(R.string.target_value),
                    valueText = limitAmount(mode, settings.taperTargetValue),
                    onMinus = { vm.updateSettings { it.copy(taperTargetValue = max(0.0, it.taperTargetValue - stepValue)) } },
                    onPlus = { vm.updateSettings { it.copy(taperTargetValue = it.taperTargetValue + stepValue) } },
                    onClickValue = { editing = LimitEdit.TARGET },
                )
                DateRow(stringResource(R.string.target_date), Fmt.dateLong(settings.taperTargetDay)) { pickTargetDate = true }
                Spacer(Modifier.height(10.dp))
                if (settings.taperTargetDay <= settings.taperStartDay) {
                    Text(stringResource(R.string.taper_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(
                            R.string.taper_summary,
                            limitAmount(mode, limitToday.toDouble()),
                            limitAmount(mode, settings.taperTargetValue),
                            Fmt.dateLong(settings.taperTargetDay),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // spacing between pouches
        SoftCard {
            Text(stringResource(R.string.tolerance_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            ToleranceSettings(settings, entries) { m -> vm.updateSettings { it.copy(intervalMode = m) } }
        }
    }

    editing?.let { edit ->
        NumberDialog(
            title = stringResource(edit.title),
            initial = Fmt.mg(
                when (edit) {
                    LimitEdit.BASE -> Limits.baseLimit(settings)
                    LimitEdit.START -> settings.taperStartValue
                    LimitEdit.TARGET -> settings.taperTargetValue
                },
            ),
            decimal = false,
            suffix = unit,
            onDismiss = { editing = null },
            onConfirm = { v ->
                when (edit) {
                    LimitEdit.BASE -> setBase(v)
                    LimitEdit.START -> vm.updateSettings { it.copy(taperStartValue = v) }
                    LimitEdit.TARGET -> vm.updateSettings { it.copy(taperTargetValue = v) }
                }
                editing = null
            },
        )
    }
    if (pickStartDate) {
        DateDialog(
            initialEpochDay = settings.taperStartDay,
            onDismiss = { pickStartDate = false },
            onConfirm = { d -> vm.updateSettings { it.copy(taperStartDay = d) }; pickStartDate = false },
        )
    }
    if (pickTargetDate) {
        DateDialog(
            initialEpochDay = settings.taperTargetDay,
            onDismiss = { pickTargetDate = false },
            onConfirm = { d -> vm.updateSettings { it.copy(taperTargetDay = d) }; pickTargetDate = false },
        )
    }
}

/** The limit values typed in a dialog. */
private enum class LimitEdit(@StringRes val title: Int) {
    BASE(R.string.daily_limit),
    START(R.string.start_value),
    TARGET(R.string.target_value),
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(value) }
    }
}

/** Off / hold / lower, with the levels they mean for this user right now. */
@Composable
private fun ToleranceSettings(settings: Settings, entries: List<LogEntry>, onMode: (IntervalMode) -> Unit) {
    val recent = remember(entries) { entries.filter { it.timestamp >= System.currentTimeMillis() - IntervalPlan.DATA_MS } }
    val usual = remember(recent, settings) {
        val now = System.currentTimeMillis()
        IntervalPlan.usualLevel(recent, settings, now - IntervalPlan.WINDOW_MS, now)
    }
    val lower = remember(recent, settings) {
        IntervalPlan.threshold(recent, settings.copy(intervalMode = IntervalMode.LOWER), System.currentTimeMillis())
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = listOf(IntervalMode.OFF to R.string.interval_off, IntervalMode.KEEP to R.string.interval_keep, IntervalMode.LOWER to R.string.interval_lower)
        modes.forEachIndexed { i, (m, res) ->
            SegmentedButton(
                selected = settings.intervalMode == m,
                onClick = { onMode(m) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
            ) { Text(stringResource(res)) }
        }
    }
    Spacer(Modifier.height(10.dp))
    val small = MaterialTheme.typography.bodySmall
    if (usual == null) {
        Text(stringResource(R.string.interval_learning), style = small)
    } else {
        val ng = Pharmacokinetics.toNgPerMl(usual, settings.bodyWeightKg, Pharmacokinetics.NICOTINE_VD_L_PER_KG)
        Text(stringResource(R.string.interval_usual, Fmt.oneDecimal(usual), Fmt.ngMl(ng)), style = small)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.interval_keep_rule, Fmt.oneDecimal(usual)), style = small)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.interval_lower_rule, Fmt.oneDecimal(lower ?: usual * IntervalPlan.LOWER_SHARE)), style = small)
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.interval_never_blocks), style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.tolerance_honest), style = small, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

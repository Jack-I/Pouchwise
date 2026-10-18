package app.pouchwise.ui.screens

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
import app.pouchwise.model.DayKeys
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
    val today = DayKeys.epochDay(now, settings.dayStartHour)
    val mode = settings.limitMode
    val unit = if (mode == LimitMode.COUNT) stringResource(R.string.unit_pouches) else stringResource(R.string.unit_mg)
    val limitToday = Limits.limitForDayRounded(settings, today)
    val stepValue = if (mode == LimitMode.COUNT) 1.0 else 5.0

    var editLimit by remember { mutableStateOf(false) }
    var editStart by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf(false) }
    var pickStartDate by remember { mutableStateOf(false) }
    var pickTargetDate by remember { mutableStateOf(false) }

    fun baseLimit(s: Settings): Double = if (s.limitMode == LimitMode.COUNT) s.dailyLimitCount.toDouble() else s.dailyLimitMg

    fun setBase(v: Double) = vm.updateSettings { s ->
        val clamped = max(0.0, v)
        if (s.limitMode == LimitMode.COUNT) s.copy(dailyLimitCount = clamped.roundToInt()) else s.copy(dailyLimitMg = clamped)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.plan_title), stringResource(R.string.plan_subtitle))

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
                        Text("$unit ${stringResource(R.string.per_day)} · ${stringResource(R.string.target_reached)}", style = MaterialTheme.typography.bodyLarge, color = onSec)
                    } else {
                        Text(stringResource(R.string.daily_limit_caption), style = MaterialTheme.typography.labelLarge, color = onSec)
                        Text("$limitToday", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = onSec)
                        Text("$unit ${stringResource(R.string.per_day)}", style = MaterialTheme.typography.bodyLarge, color = onSec)
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
                    onClick = { vm.updateSettings { it.copy(limitMode = LimitMode.COUNT, taperEnabled = false) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.mode_count)) }
                SegmentedButton(
                    selected = mode == LimitMode.MG,
                    onClick = { vm.updateSettings { it.copy(limitMode = LimitMode.MG, taperEnabled = false) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.mode_mg)) }
            }
        }

        // fixed limit
        SoftCard {
            if (settings.taperEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.limit_today), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("$limitToday $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                val base = baseLimit(settings)
                StepperRow(
                    label = stringResource(R.string.daily_limit),
                    valueText = "${Fmt.mg(base)} $unit",
                    onMinus = { setBase(base - stepValue) },
                    onPlus = { setBase(base + stepValue) },
                    onClickValue = { editLimit = true },
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
                                    val start = baseLimit(s)
                                    s.copy(
                                        taperEnabled = true,
                                        taperStartDay = today,
                                        taperStartValue = start,
                                        taperTargetDay = today + 60,
                                        taperTargetValue = max(if (s.limitMode == LimitMode.COUNT) 1.0 else 5.0, (start / 2.0).let { if (s.limitMode == LimitMode.COUNT) it.roundToInt().toDouble() else it }),
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
                    valueText = "${Fmt.mg(settings.taperStartValue)} $unit",
                    onMinus = { vm.updateSettings { it.copy(taperStartValue = max(0.0, it.taperStartValue - stepValue)) } },
                    onPlus = { vm.updateSettings { it.copy(taperStartValue = it.taperStartValue + stepValue) } },
                    onClickValue = { editStart = true },
                )
                DateRow(stringResource(R.string.start_date), Fmt.dateLong(settings.taperStartDay)) { pickStartDate = true }
                Spacer(Modifier.height(8.dp))
                StepperRow(
                    label = stringResource(R.string.target_value),
                    valueText = "${Fmt.mg(settings.taperTargetValue)} $unit",
                    onMinus = { vm.updateSettings { it.copy(taperTargetValue = max(0.0, it.taperTargetValue - stepValue)) } },
                    onPlus = { vm.updateSettings { it.copy(taperTargetValue = it.taperTargetValue + stepValue) } },
                    onClickValue = { editTarget = true },
                )
                DateRow(stringResource(R.string.target_date), Fmt.dateLong(settings.taperTargetDay)) { pickTargetDate = true }
                Spacer(Modifier.height(10.dp))
                if (settings.taperTargetDay <= settings.taperStartDay) {
                    Text(stringResource(R.string.taper_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(
                            R.string.taper_summary,
                            "$limitToday $unit",
                            "${Fmt.mg(settings.taperTargetValue)} $unit",
                            Fmt.dateLong(settings.taperTargetDay),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (editLimit) {
        NumberDialog(
            title = stringResource(R.string.daily_limit),
            initial = Fmt.mg(baseLimit(settings)),
            decimal = mode == LimitMode.MG,
            suffix = unit,
            onDismiss = { editLimit = false },
            onConfirm = { setBase(it); editLimit = false },
        )
    }
    if (editStart) {
        NumberDialog(
            title = stringResource(R.string.start_value),
            initial = Fmt.mg(settings.taperStartValue),
            decimal = mode == LimitMode.MG,
            suffix = unit,
            onDismiss = { editStart = false },
            onConfirm = { v -> vm.updateSettings { it.copy(taperStartValue = v) }; editStart = false },
        )
    }
    if (editTarget) {
        NumberDialog(
            title = stringResource(R.string.target_value),
            initial = Fmt.mg(settings.taperTargetValue),
            decimal = mode == LimitMode.MG,
            suffix = unit,
            onDismiss = { editTarget = false },
            onConfirm = { v -> vm.updateSettings { it.copy(taperTargetValue = v) }; editTarget = false },
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

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(value) }
    }
}

package app.pouchwise.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.model.PkParams
import app.pouchwise.model.ThemeMode
import app.pouchwise.ui.components.*
import app.pouchwise.util.Fmt
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onManagePouches: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pk = settings.pk
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }.getOrDefault("")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_pouches))
            SoftCard {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onManagePouches),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.manage_pouches), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }

            SectionTitle(stringResource(R.string.settings_day))
            SoftCard {
                StepperRow(
                    label = stringResource(R.string.day_starts_at),
                    valueText = Fmt.hourLabel(settings.dayStartHour),
                    onMinus = { vm.updateSettings { it.copy(dayStartHour = (it.dayStartHour - 1).coerceIn(0, 12)) } },
                    onPlus = { vm.updateSettings { it.copy(dayStartHour = (it.dayStartHour + 1).coerceIn(0, 12)) } },
                    hint = stringResource(R.string.day_starts_hint),
                )
            }

            SectionTitle(stringResource(R.string.settings_model))
            SoftCard {
                StepperRow(
                    label = stringResource(R.string.weight),
                    valueText = Fmt.mg(settings.bodyWeightKg),
                    onMinus = { vm.updateSettings { it.copy(bodyWeightKg = (it.bodyWeightKg - 1).coerceIn(30.0, 250.0)) } },
                    onPlus = { vm.updateSettings { it.copy(bodyWeightKg = (it.bodyWeightKg + 1).coerceIn(30.0, 250.0)) } },
                    hint = stringResource(R.string.weight_hint),
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                SliderRow(
                    label = stringResource(R.string.absorption),
                    valueText = "${(pk.absorptionFraction * 100).roundToInt()}%",
                    value = pk.absorptionFraction.toFloat(),
                    range = 0.10f..0.70f,
                    steps = 11,
                    onChange = { v -> vm.updateSettings { it.copy(pk = it.pk.copy(absorptionFraction = (v * 100).roundToInt() / 100.0)) } },
                )
                StepperRow(
                    label = stringResource(R.string.absorption_minutes),
                    valueText = pk.absorptionMinutes.toString(),
                    onMinus = { vm.updateSettings { it.copy(pk = it.pk.copy(absorptionMinutes = (it.pk.absorptionMinutes - 5).coerceIn(5, 90))) } },
                    onPlus = { vm.updateSettings { it.copy(pk = it.pk.copy(absorptionMinutes = (it.pk.absorptionMinutes + 5).coerceIn(5, 90))) } },
                    hint = stringResource(R.string.pouch_time_hint),
                )
                StepperRow(
                    label = stringResource(R.string.nic_half_life),
                    valueText = Fmt.oneDecimal(pk.nicotineHalfLifeHours),
                    onMinus = { vm.updateSettings { it.copy(pk = it.pk.copy(nicotineHalfLifeHours = (it.pk.nicotineHalfLifeHours - 0.25).coerceIn(0.5, 6.0))) } },
                    onPlus = { vm.updateSettings { it.copy(pk = it.pk.copy(nicotineHalfLifeHours = (it.pk.nicotineHalfLifeHours + 0.25).coerceIn(0.5, 6.0))) } },
                )
                StepperRow(
                    label = stringResource(R.string.cot_half_life),
                    valueText = Fmt.oneDecimal(pk.cotinineHalfLifeHours),
                    onMinus = { vm.updateSettings { it.copy(pk = it.pk.copy(cotinineHalfLifeHours = (it.pk.cotinineHalfLifeHours - 1).coerceIn(6.0, 40.0))) } },
                    onPlus = { vm.updateSettings { it.copy(pk = it.pk.copy(cotinineHalfLifeHours = (it.pk.cotinineHalfLifeHours + 1).coerceIn(6.0, 40.0))) } },
                )
                SliderRow(
                    label = stringResource(R.string.cot_yield),
                    valueText = "${(pk.cotinineYield * 100).roundToInt()}%",
                    value = pk.cotinineYield.toFloat(),
                    range = 0.50f..0.90f,
                    steps = 7,
                    onChange = { v -> vm.updateSettings { it.copy(pk = it.pk.copy(cotinineYield = (v * 100).roundToInt() / 100.0)) } },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.model_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.updateSettings { it.copy(pk = PkParams()) } }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.reset_defaults))
                }
            }

            SectionTitle(stringResource(R.string.settings_appearance))
            SoftCard {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                    modes.forEachIndexed { i, (m, res) ->
                        SegmentedButton(
                            selected = settings.themeMode == m,
                            onClick = { vm.updateSettings { it.copy(themeMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                        ) { Text(stringResource(res)) }
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_data))
            SoftCard {
                Text(stringResource(R.string.export_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.exportCsv(context) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_csv))
                    }
                    OutlinedButton(onClick = { vm.exportJson(context) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_json))
                    }
                }
            }

            SectionTitle(stringResource(R.string.about))
            SoftCard {
                Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

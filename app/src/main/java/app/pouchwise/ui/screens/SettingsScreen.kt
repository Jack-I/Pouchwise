package app.pouchwise.ui.screens

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.ThemeMode
import app.pouchwise.ui.components.*
import app.pouchwise.util.Fmt
import app.pouchwise.util.Money

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onManagePouches: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val startImport = rememberImportFlow(vm)
    val pk = settings.pk
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            (info.versionName ?: "") to PackageInfoCompat.getLongVersionCode(info)
        }.getOrDefault("" to 0L)
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings), onBack) }) { inner ->
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

            SectionTitle(stringResource(R.string.settings_spending))
            SoftCard {
                val baseline = settings.baselinePerDay
                StepperRow(
                    label = stringResource(R.string.baseline_label),
                    valueText = baseline?.toString() ?: "—",
                    onMinus = { vm.updateSettings { it.copy(baselinePerDay = ((it.baselinePerDay ?: 11) - 1).coerceIn(0, 200)) } },
                    onPlus = { vm.updateSettings { it.copy(baselinePerDay = ((it.baselinePerDay ?: 9) + 1).coerceIn(0, 200)) } },
                    hint = stringResource(R.string.baseline_hint),
                )
                Spacer(Modifier.height(12.dp))
                var currency by remember { mutableStateOf(settings.currency) }
                var typing by remember { mutableStateOf(false) }
                // While typing, saves of earlier keystrokes are still arriving and must not overwrite the field.
                LaunchedEffect(settings.currency, typing) { if (!typing) currency = settings.currency }
                OutlinedTextField(
                    value = currency,
                    onValueChange = { v ->
                        currency = v.take(8)
                        vm.updateSettings { it.copy(currency = v.take(8)) }
                    },
                    label = { Text(stringResource(R.string.currency_label)) },
                    placeholder = { Text(Money.regionSymbol()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { typing = it.isFocused },
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
                StepperRow(
                    label = stringResource(R.string.absorption_minutes),
                    valueText = pk.absorptionMinutes.toString(),
                    onMinus = { vm.updateSettings { it.copy(pk = it.pk.copy(absorptionMinutes = (it.pk.absorptionMinutes - 5).coerceIn(5, PouchTiming.MAX_POUCH_MINUTES))) } },
                    onPlus = { vm.updateSettings { it.copy(pk = it.pk.copy(absorptionMinutes = (it.pk.absorptionMinutes + 5).coerceIn(5, PouchTiming.MAX_POUCH_MINUTES))) } },
                    hint = stringResource(R.string.pouch_time_hint),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.model_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // Per-app language is a system feature from Android 13; older phones keep the phone's language.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SectionTitle(stringResource(R.string.settings_language))
                SoftCard { LanguagePicker() }
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
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.exportFeltCsv(context) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.export_felt_csv))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = startImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.import_json))
                }
            }

            SectionTitle(stringResource(R.string.about))
            SoftCard {
                Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.version, version.first, version.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** System default, English or Russian; the system stores the choice and recreates the screen in the new language. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    var current by remember { mutableStateOf(manager?.applicationLocales?.takeUnless { it.isEmpty }?.get(0)?.language) }
    val options = listOf(
        null to stringResource(R.string.language_system),
        "en" to stringResource(R.string.language_en),
        "ru" to stringResource(R.string.language_ru),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = {
                    current = tag
                    manager?.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label) }
        }
    }
}

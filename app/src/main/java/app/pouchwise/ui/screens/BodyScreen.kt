package app.pouchwise.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.model.Pharmacokinetics
import app.pouchwise.model.PouchTiming
import app.pouchwise.ui.components.*
import app.pouchwise.ui.theme.AppColors
import app.pouchwise.util.Fmt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val RANGES_MS = listOf(24L, 48L, 7L * 24).map { it * 3_600_000L }

@Composable
fun BodyScreen(vm: AppViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val pk = settings.pk

    var rangeIdx by rememberSaveable { mutableIntStateOf(0) }
    val pastMs = RANGES_MS[rangeIdx]

    val doses = remember(entries, settings) { entries.map { PouchTiming.toDose(it, settings) } }
    val levels = remember(doses, pk, now) { Pharmacokinetics.levelsAt(now, doses, pk) }
    val clearance = remember(doses, pk, now) { Pharmacokinetics.clearance(now, doses, pk) }

    val chart = remember(doses, pk, now, pastMs) {
        val clearEnd = max(clearance.nicotineClearMs ?: now, clearance.cotinineClearMs ?: now)
        val futureMs = min(max(clearEnd - now, 2L * 3_600_000L), pastMs)
        val step = max((pastMs + futureMs) / 240L, 60_000L)
        val past = Pharmacokinetics.series(now - pastMs, now, step, doses, pk)
        val future = Pharmacokinetics.series(now, now + futureMs, step, doses, pk)
        past to future
    }

    val nicNg = Pharmacokinetics.toNgPerMl(levels.nicotineMg, settings.bodyWeightKg, Pharmacokinetics.NICOTINE_VD_L_PER_KG)
    val cotNg = Pharmacokinetics.toNgPerMl(levels.cotinineMg, settings.bodyWeightKg, Pharmacokinetics.COTININE_VD_L_PER_KG)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.body_title), stringResource(R.string.body_subtitle))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = stringResource(R.string.nicotine),
                value = stringResource(R.string.mg_value, Fmt.oneDecimal(levels.nicotineMg)),
                sub = stringResource(R.string.approx_ng_ml, Fmt.ngMl(nicNg)),
                accent = AppColors.nicotine,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.cotinine),
                value = stringResource(R.string.mg_value, Fmt.oneDecimal(levels.cotinineMg)),
                sub = stringResource(R.string.approx_ng_ml, Fmt.ngMl(cotNg)),
                accent = AppColors.cotinine,
                modifier = Modifier.weight(1f),
            )
        }

        SoftCard {
            ClearanceLine(stringResource(R.string.nicotine), clearance.nicotineClearMs, now)
            Spacer(Modifier.height(6.dp))
            ClearanceLine(stringResource(R.string.cotinine), clearance.cotinineClearMs, now)
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val labels = listOf(R.string.range_24h, R.string.range_48h, R.string.range_7d)
            labels.forEachIndexed { i, res ->
                SegmentedButton(
                    selected = rangeIdx == i,
                    onClick = { rangeIdx = i },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                ) { Text(stringResource(res)) }
            }
        }

        SoftCard {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.no_data_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BodyChart(
                    past = chart.first,
                    future = chart.second,
                    nowMs = now,
                    nicotineColor = AppColors.nicotine,
                    cotinineColor = AppColors.cotinine,
                )
                Spacer(Modifier.height(10.dp))
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

        Text(
            stringResource(
                R.string.model_summary,
                Fmt.mg(pk.nicotineHalfLifeHours), Fmt.mg(pk.cotinineHalfLifeHours),
                (pk.absorptionFraction * 100).roundToInt(), pk.absorptionMinutes,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClearanceLine(name: String, clearMs: Long?, nowMs: Long) {
    val text = if (clearMs == null || clearMs <= nowMs) stringResource(R.string.already_clear, name)
               else stringResource(R.string.clears_in, name, durationText(clearMs - nowMs), Fmt.timeOrDayTime(clearMs, nowMs))
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

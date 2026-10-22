package app.pouchwise.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.model.DayKeys
import app.pouchwise.model.Pharmacokinetics
import app.pouchwise.model.PouchTiming
import app.pouchwise.ui.theme.AppColors
import app.pouchwise.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** A level measured in studies: [lo], or [lo]–[hi], in ng/mL; what it means and where it comes from. */
private class Level(
    val lo: Double,
    val hi: Double?,
    @StringRes val label: Int,
    val source: String,
    val prefix: String = "",
    val suffix: String = "",
)

private val NICOTINE = listOf(
    Level(0.87, null, R.string.lv_receptors_half, "Brody 2006"),
    Level(2.0, 3.0, R.string.lv_morning, "Xue 2010"),
    Level(5.0, 20.0, R.string.lv_nrt, "Benowitz 2009"),
    Level(7.0, 18.0, R.string.lv_nausea_new, "Srivastava 1991"),
    Level(10.0, 50.0, R.string.lv_smokers_afternoon, "Benowitz 2009"),
    Level(29.0, 37.0, R.string.lv_snus_peak, "Holm 1992"),
    Level(800.0, null, R.string.lv_poisoning, "Maessen 2020", suffix = "+"),
)

private val COTININE = listOf(
    Level(3.0, null, R.string.lv_non_user, "SRNT 2020", prefix = "< "),
    Level(131.0, null, R.string.lv_us_smokers, "Rostron 2015"),
    Level(179.0, null, R.string.lv_us_smokeless, "Rostron 2015"),
    Level(250.0, 300.0, R.string.lv_typical_smokers, "Benowitz 2009"),
    Level(300.0, null, R.string.lv_snus_users, "Söderström 2021", prefix = "≈ "),
)

/** A finding, with an optional lead-in in bold and the studies behind it. */
private class Finding(@StringRes val text: Int, val source: String = "", @StringRes val title: Int? = null)

private val HARM = listOf(
    Finding(R.string.lv_harm_no_safe),
    Finding(R.string.lv_harm_no_link),
    Finding(R.string.lv_harm_snus_mortality, "Byhamre 2020"),
    Finding(R.string.lv_harm_diabetes, "Carlsson 2017"),
    Finding(R.string.lv_harm_pulse, "Mallock-Ohnesorg 2024; Cheraghi 2024"),
)

private val CARE = listOf(
    Finding(R.string.lv_care_pregnancy, "Surgeon General 2014; Wikström 2010", R.string.lv_care_pregnancy_title),
    Finding(R.string.lv_care_young, "Surgeon General 2016", R.string.lv_care_young_title),
    Finding(R.string.lv_care_heart, "Arefalk 2014", R.string.lv_care_heart_title),
)

private val SOURCES = listOf(
    "Arefalk G et al., Circulation 2014" to "https://doi.org/10.1161/CIRCULATIONAHA.113.007252",
    "Benowitz NL et al., Handb Exp Pharmacol 2009" to "https://pmc.ncbi.nlm.nih.gov/articles/PMC2953858/",
    "Brody AL et al., Arch Gen Psychiatry 2006" to "https://doi.org/10.1001/archpsyc.63.8.907",
    "Byhamre ML et al., Int J Epidemiol 2020" to "https://doi.org/10.1093/ije/dyaa197",
    "Carlsson S et al., J Intern Med 2017" to "https://doi.org/10.1111/joim.12592",
    "Cheraghi M et al., Eur Heart J Open 2024" to "https://doi.org/10.1093/ehjopen/oeae098",
    "Holm H et al., Psychopharmacology 1992" to "https://doi.org/10.1007/BF02247429",
    "Maessen GC et al., Clin Toxicol 2020" to "https://doi.org/10.1080/15563650.2019.1636994",
    "Mallock-Ohnesorg N et al., Front Pharmacol 2024" to "https://doi.org/10.3389/fphar.2024.1392027",
    "Rostron BL et al., Cancer Epidemiol Biomarkers Prev 2015" to "https://pmc.ncbi.nlm.nih.gov/articles/PMC5134927/",
    "Söderström E et al., Int J Environ Res Public Health 2021" to "https://pmc.ncbi.nlm.nih.gov/articles/PMC8583682/",
    "Srivastava ED et al., Psychopharmacology 1991" to "https://doi.org/10.1007/BF02316865",
    "SRNT: Benowitz NL et al., Nicotine Tob Res 2020" to "https://pmc.ncbi.nlm.nih.gov/articles/PMC7882145/",
    "Surgeon General 2014, ch. 5: Nicotine" to "https://www.ncbi.nlm.nih.gov/books/NBK294308/",
    "Surgeon General 2016, ch. 1: E-cigarettes and youth" to "https://www.ncbi.nlm.nih.gov/books/NBK538684/",
    "Wikström AK et al., Epidemiology 2010" to "https://doi.org/10.1097/EDE.0b013e3181f20d7e",
    "Xue Y et al., Pharmacol Biochem Behav 2010" to "https://pmc.ncbi.nlm.nih.gov/articles/PMC2832319/",
)

private class Line(val value: String, val label: String, val source: String, val yours: Boolean)

/** Levels from studies in rising order, with the user's own value in its place among them. */
@Composable
fun LevelsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    // In ng/mL: the highest nicotine since the day began, as most nicotine levels from studies are peaks; and the
    // cotinine average over the last 24 hours, since it swings by about a third between morning and evening.
    val yours by produceState<Pair<Double, Double>?>(initialValue = null, entries, settings, now) {
        value = withContext(Dispatchers.Default) {
            val clock = maxOf(now, System.currentTimeMillis())
            val doses = entries.map { PouchTiming.toDose(it, settings) }
            val dayStart = DayKeys.startOfDayMs(DayKeys.epochDay(clock, settings.dayStartHour), settings.dayStartHour)
            val peakMg = Pharmacokinetics.series(dayStart, clock, 5 * 60_000L, doses, settings.pk).maxOfOrNull { it.second.nicotineMg } ?: 0.0
            val dayAgo = clock - 24 * 3_600_000L
            val cotinineMg = (0..144).map { Pharmacokinetics.levelsAt(dayAgo + it * 600_000L, doses, settings.pk).cotinineMg }.average()
            Pharmacokinetics.toNgPerMl(peakMg, settings.bodyWeightKg, Pharmacokinetics.NICOTINE_VD_L_PER_KG) to
                Pharmacokinetics.toNgPerMl(cotinineMg, settings.bodyWeightKg, Pharmacokinetics.COTININE_VD_L_PER_KG)
        }
    }
    val nicotine = lines(NICOTINE, yours?.first, R.string.lv_you_peak)
    val cotinine = lines(COTININE, yours?.second, R.string.lv_you_day_average)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.levels_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.levels_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Heading(R.string.lv_nicotine, R.string.lv_nicotine_unit) }
            items(nicotine) { LevelRow(it) }
            item { Heading(R.string.lv_cotinine, R.string.lv_cotinine_unit) }
            items(cotinine) { LevelRow(it) }
            item { Heading(R.string.lv_harm) }
            items(HARM) { Bullet(it) }
            item { Heading(R.string.lv_care) }
            items(CARE) { Bullet(it) }
            item { Heading(R.string.lv_sources) }
            items(SOURCES) { (title, url) ->
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(url) }.padding(vertical = 6.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.levels_checked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun lines(levels: List<Level>, yours: Double?, @StringRes yoursLabel: Int): List<Line> {
    val number = remember { NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 } }
    val out = levels.map {
        val range = number.format(it.lo) + (it.hi?.let { hi -> "–" + number.format(hi) } ?: "")
        Line(it.prefix + range + it.suffix, stringResource(it.label), it.source, yours = false)
    }.toMutableList()
    if (yours != null) {
        val at = levels.indexOfFirst { it.lo > yours }.let { if (it < 0) levels.size else it }
        out.add(at, Line(Fmt.ngMl(yours), stringResource(yoursLabel), "", yours = true))
    }
    return out
}

@Composable
private fun Heading(@StringRes title: Int, @StringRes unit: Int? = null) {
    Row(Modifier.padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (unit != null) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelRow(line: Line) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (line.yours) AppColors.plan.copy(alpha = 0.18f) else MaterialTheme.colorScheme.background)
            .padding(horizontal = 6.dp, vertical = 7.dp),
    ) {
        Text(
            line.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 76.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(line.label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (line.yours) FontWeight.Bold else null)
            if (line.source.isNotEmpty()) Text(line.source, style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Bullet(finding: Finding) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val title = finding.title?.let { stringResource(it) }
    val text = stringResource(finding.text)
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
        Text(
            buildAnnotatedString {
                if (title != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
                    append(": ")
                }
                append(text)
                if (finding.source.isNotEmpty()) {
                    withStyle(SpanStyle(color = muted, fontSize = 12.sp)) { append("  " + finding.source) }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

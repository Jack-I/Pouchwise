package app.pouchwise.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pouchwise.R
import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.model.GardenLevel
import app.pouchwise.model.LimitMode
import app.pouchwise.ui.theme.AppColors
import app.pouchwise.util.Fmt
import kotlin.math.roundToInt

val CardShape = RoundedCornerShape(24.dp)

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Top bar of a screen opened over the tabs, with a back arrow. */
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** A small rounded label next to a log entry's name. */
@Composable
fun Tag(text: String, background: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = style, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** The launcher icon in miniature: the drawing on the brand-blue disc, cropped the way the launcher crops it. */
@Composable
fun AppLogo(size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = ImageBitmap.imageResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            filterQuality = FilterQuality.High,
            // The foreground canvas is 108 units, of which the launcher shows the middle 72.
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, accent: Color? = null) {
    SoftCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
        )
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A labelled row with - / + buttons; tapping the value opens direct entry when [onClickValue] is given. */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    onClickValue: (() -> Unit)? = null,
    enabled: Boolean = true,
    hint: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onMinus, enabled = enabled) {
                Icon(Icons.Filled.Remove, contentDescription = null)
            }
            Text(
                valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onClickValue != null && enabled) Modifier.clickable { onClickValue() } else Modifier)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
            FilledTonalIconButton(onClick = onPlus, enabled = enabled) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 12) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun LegendSwatch(color: Color, text: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) {
            Row {
                repeat(3) { i ->
                    Box(Modifier.width(6.dp).height(3.dp).background(color))
                    if (i < 2) Spacer(Modifier.width(3.dp))
                }
            }
        } else {
            Box(Modifier.width(24.dp).height(3.dp).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A limit with its unit: "1 pouch", "3 пауча", "40 mg". */
@Composable
fun limitAmount(mode: LimitMode, value: Double): String {
    if (mode == LimitMode.MG) return "${Fmt.mg(value)} ${stringResource(R.string.unit_mg)}"
    val n = Math.round(value).toInt()
    return pluralStringResource(R.plurals.count_pouches, n, n)
}

/** The unit alone, for a number shown apart from it. */
@Composable
fun limitUnit(mode: LimitMode, value: Int): String =
    if (mode == LimitMode.MG) stringResource(R.string.unit_mg) else pluralStringResource(R.plurals.pouches_word, value)

/** Human-readable duration from milliseconds. */
@Composable
fun durationText(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.time_now)
        minutes < 60 -> stringResource(R.string.duration_m, minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.duration_h_m, (minutes / 60).toInt(), (minutes % 60).toInt())
        else -> stringResource(R.string.duration_d_h, (minutes / (24 * 60)).toInt(), ((minutes % (24 * 60)) / 60).toInt())
    }
}

/** A day level's tile colour on [under], the card behind it; a little muted on a dark card. */
fun gardenColor(level: GardenLevel, under: Color): Color {
    if (level == GardenLevel.EMPTY) return Color.Transparent
    val c = AppColors.garden[(level.ordinal - 1).coerceIn(0, AppColors.garden.lastIndex)]
    return if (under.luminance() < 0.5f) c.copy(alpha = 0.85f).compositeOver(under) else c
}

/** Near-black or white text, whichever reads better on [tile]. */
fun onGardenColor(tile: Color): Color = if (tile.luminance() > 0.2f) Color(0xFF111827) else Color.White

@Composable
fun BigEmoji(text: String, size: Int = 56) {
    Text(text, fontSize = size.sp, lineHeight = (size + 8).sp)
}

fun feltLabel(felt: Felt): Int = when (felt) {
    Felt.OVERWHELM -> R.string.felt_overwhelm
    Felt.HIT -> R.string.felt_hit
    Felt.A_BIT -> R.string.felt_a_bit
    Felt.NOTHING -> R.string.felt_nothing
}

/** Whole minutes from the toss-in to an answer. */
fun feltMinutes(startMs: Long, atMs: Long): Int = ((atMs - startMs) / 60_000.0).roundToInt().coerceAtLeast(0)

/** Overwhelm / Hit / A bit / Nothing in one row, [selected] filled. */
@Composable
fun FeltChips(selected: Felt?, onTap: (Felt) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (f in Felt.strongestFirst) {
            val on = f == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(shape)
                    .then(
                        if (on) Modifier.background(MaterialTheme.colorScheme.primary)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), shape),
                    )
                    .selectable(selected = on, role = Role.RadioButton) { onTap(f) },
                contentAlignment = Alignment.Center,
            ) {
                FittingText(
                    stringResource(feltLabel(f)),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** One line of text that shrinks until it fits, for labels that must not be cut at large font sizes. */
@Composable
private fun FittingText(text: String, style: TextStyle, color: Color) {
    var size by remember(text, style) { mutableStateOf(style.fontSize) }
    Text(
        text,
        style = style.copy(fontSize = size),
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && size.value > 8f) size = size * 0.9f },
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

/** The answers in order, e.g. "Hit · 3 min → Overwhelm · 20 min"; a recalled one shows its pull-out as "~30 min". */
@Composable
fun feltHistory(marks: List<FeltMark>, startMs: Long): String =
    marks.mapNotNull { m ->
        Felt.of(m.level)?.let {
            stringResource(if (m.recalled) R.string.felt_mark_recalled_at else R.string.felt_mark_at, stringResource(feltLabel(it)), feltMinutes(startMs, m.atMs))
        }
    }.joinToString(" → ")

/** The answers as a short tag, e.g. "hit → overwhelm"; a long run keeps its first and last. */
@Composable
fun feltChain(marks: List<FeltMark>): String {
    val words = marks.mapNotNull { m -> Felt.of(m.level)?.let { stringResource(feltLabel(it)).lowercase() } }
    return if (words.size <= 3) words.joinToString(" → ") else "${words.first()} → … → ${words.last()}"
}

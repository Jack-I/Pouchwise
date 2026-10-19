package app.pouchwise.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pouchwise.R
import app.pouchwise.model.GardenLevel
import app.pouchwise.model.LimitMode
import app.pouchwise.ui.theme.AppColors
import app.pouchwise.util.Fmt

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

fun gardenColor(level: GardenLevel, isDark: Boolean): Color {
    if (level == GardenLevel.EMPTY) return Color.Transparent
    val c = AppColors.garden[(level.ordinal - 1).coerceIn(0, AppColors.garden.lastIndex)]
    return if (isDark) c.copy(alpha = 0.55f) else c.copy(alpha = 0.75f)
}

@Composable
fun BigEmoji(text: String, size: Int = 56) {
    Text(text, fontSize = size.sp, lineHeight = (size + 8).sp)
}

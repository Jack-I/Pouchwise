package app.pouchwise.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E88E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFB),
    onPrimaryContainer = Color(0xFF0D3C70),
    secondary = Color(0xFF43A047),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F4E3),
    onSecondaryContainer = Color(0xFF2E5A23),
    tertiary = Color(0xFF8E24AA),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E5F5),
    onTertiaryContainer = Color(0xFF4A148C),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFDE3E3),
    onErrorContainer = Color(0xFF7A1C1C),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2A44),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2A44),
    surfaceVariant = Color(0xFFEEF4FC),
    onSurfaceVariant = Color(0xFF5C6B85),
    surfaceContainer = Color(0xFFF3F6FB),
    surfaceContainerHigh = Color(0xFFEAF0F8),
    outline = Color(0xFFC7D2E3),
    outlineVariant = Color(0xFFE1E8F2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF062B4F),
    primaryContainer = Color(0xFF1A3A5C),
    onPrimaryContainer = Color(0xFFD6E8FB),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF0F3A14),
    secondaryContainer = Color(0xFF1F3B22),
    onSecondaryContainer = Color(0xFFCDEBCF),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF3A0B47),
    tertiaryContainer = Color(0xFF4A2A55),
    onTertiaryContainer = Color(0xFFF1D9F6),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF4A0E0E),
    errorContainer = Color(0xFF5C1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111827),
    onBackground = Color(0xFFE5E9F2),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E9F2),
    surfaceVariant = Color(0xFF1B2537),
    onSurfaceVariant = Color(0xFFA7B3C8),
    surfaceContainer = Color(0xFF182134),
    surfaceContainerHigh = Color(0xFF1F2A3F),
    outline = Color(0xFF3C4A63),
    outlineVariant = Color(0xFF2B3649),
)

/** Colours that are not part of the Material scheme. */
object AppColors {
    val nicotine = Color(0xFF1E88E5)
    val cotinine = Color(0xFF8E24AA)
    /** The interval plan: its level on the chart, the "next" row and the early marker. */
    val plan = Color(0xFFFFB300)
    /** Tile colours for the seven day levels, best to worst: a hue apart each, so neighbours do not blur. */
    val garden = listOf(
        Color(0xFF0277BD), Color(0xFF00897B), Color(0xFF7CB342), Color(0xFFFDD835),
        Color(0xFFFB8C00), Color(0xFFE53935), Color(0xFF8E24AA),
    )
}

val PouchPalette = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA),
    Color(0xFFE53935), Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFF546E7A),
)

fun pouchColor(index: Int): Color = PouchPalette[Math.floorMod(index, PouchPalette.size)]

@Composable
fun PouchwiseTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

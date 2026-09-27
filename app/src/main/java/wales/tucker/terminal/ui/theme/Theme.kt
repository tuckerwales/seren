package wales.tucker.terminal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import wales.tucker.terminal.R
import wales.tucker.terminal.data.ThemeMode

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val MonoSmall = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 16.sp)
val MonoMedium = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, lineHeight = 20.sp)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B55C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF00006E),
    secondary = Color(0xFF006B5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9EF2E0),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF7A5260),
    tertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFFF8F8FC),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFF8F8FC),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F8),
    surfaceContainer = Color(0xFFECECF3),
    surfaceContainerHigh = Color(0xFFE6E6EE),
    surfaceContainerHighest = Color(0xFFE1E1E9),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBFC2FF),
    onPrimary = Color(0xFF151E97),
    primaryContainer = Color(0xFF323BAE),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFF82D5C4),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFF9EF2E0),
    tertiary = Color(0xFFEAB8C8),
    tertiaryContainer = Color(0xFF603B48),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33343A),
    outline = Color(0xFF91909A),
    outlineVariant = Color(0xFF46464F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

@Composable
fun TerminalTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    SystemBarAppearance(lightBars = !dark)
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

/**
 * Picks dark (for [lightBars]) or light status and navigation bar icons while in composition,
 * restoring the previous appearance afterwards. Screens with their own background, such as the
 * terminal, use it to keep the icons readable.
 */
@Composable
fun SystemBarAppearance(lightBars: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, lightBars) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

/** Accent colors users can tag hosts with. */
val HostColors = listOf(
    Color(0xFF5B6CF9), Color(0xFF00A58E), Color(0xFFE5484D), Color(0xFFF5A524),
    Color(0xFF9B5DE5), Color(0xFF00B4D8), Color(0xFFEF6FA5), Color(0xFF7C8B9C),
)

fun hostColor(index: Int): Color = HostColors[index.mod(HostColors.size)]

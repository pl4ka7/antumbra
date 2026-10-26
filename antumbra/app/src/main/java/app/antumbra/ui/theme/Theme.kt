package app.antumbra.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.antumbra.R
import app.antumbra.core.ThemeMode

val AppSans = FontFamily(
    Font(R.font.sans_regular, FontWeight.Normal),
    Font(R.font.sans_medium, FontWeight.Medium),
    Font(R.font.sans_semibold, FontWeight.SemiBold),
)

val AppMono = FontFamily(
    Font(R.font.mono_regular, FontWeight.Normal),
    Font(R.font.mono_medium, FontWeight.Medium),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A4D),
    onPrimary = Color(0xFF2A0E04),
    primaryContainer = Color(0xFF5A2410),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFD9D4C7),
    onSecondary = Color(0xFF1E1C17),
    secondaryContainer = Color(0xFF3A3530),
    onSecondaryContainer = Color(0xFFF5E6DC),
    tertiary = Color(0xFFB3A7FF),
    onTertiary = Color(0xFF1D1460),
    tertiaryContainer = Color(0xFF332A78),
    onTertiaryContainer = Color(0xFFE4DFFF),
    background = Color(0xFF15181C),
    onBackground = Color(0xFFF2EFE9),
    surface = Color(0xFF15181C),
    onSurface = Color(0xFFF2EFE9),
    surfaceVariant = Color(0xFF2A2F37),
    onSurfaceVariant = Color(0xFFBCC2CA),
    surfaceContainerLowest = Color(0xFF0F1114),
    surfaceContainerLow = Color(0xFF1B1F24),
    surfaceContainer = Color(0xFF20242A),
    surfaceContainerHigh = Color(0xFF2A2F37),
    surfaceContainerHighest = Color(0xFF343A43),
    outline = Color(0xFF6B737E),
    outlineVariant = Color(0xFF3D434C),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0606),
    inverseSurface = Color(0xFFECE9E2),
    inverseOnSurface = Color(0xFF15171A),
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A1204),
    secondary = Color(0xFF4A4F57),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3DFD7),
    onSecondaryContainer = Color(0xFF1E2127),
    tertiary = Color(0xFF5B4BD6),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4DFFF),
    onTertiaryContainer = Color(0xFF1D1460),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF15171A),
    surface = Color(0xFFF6F4EF),
    onSurface = Color(0xFF15171A),
    surfaceVariant = Color(0xFFE3DFD7),
    onSurfaceVariant = Color(0xFF555B63),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0EDE7),
    surfaceContainer = Color(0xFFEAE7E0),
    surfaceContainerHigh = Color(0xFFE4E0D9),
    surfaceContainerHighest = Color(0xFFDDD9D1),
    outline = Color(0xFF8A9099),
    outlineVariant = Color(0xFFD3CEC4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF2A2D32),
    inverseOnSurface = Color(0xFFF0EDE7),
    scrim = Color(0xFF000000),
)

/** Colours outside the Material roles. */
@Immutable
data class Signals(val safe: Color, val warning: Color, val onion: Color)

private val DarkSignals = Signals(safe = Color(0xFF4CC38A), warning = Color(0xFFF2B84B), onion = Color(0xFFB3A7FF))
private val LightSignals = Signals(safe = Color(0xFF1F7A50), warning = Color(0xFF9A6200), onion = Color(0xFF5B4BD6))

val LocalSignals = staticCompositionLocalOf { DarkSignals }

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.sans() = copy(fontFamily = AppSans)
    return Typography(
        displayLarge = base.displayLarge.sans(),
        displayMedium = base.displayMedium.sans(),
        displaySmall = base.displaySmall.sans(),
        headlineLarge = base.headlineLarge.sans().copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.sans().copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.sans().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.sans().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.sans().copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.sans().copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.sans().copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val MonoSmall = TextStyle(fontFamily = AppMono, fontSize = 12.sp, lineHeight = 16.sp)
val MonoMedium = TextStyle(fontFamily = AppMono, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AntumbraTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalSignals provides if (dark) DarkSignals else LightSignals) {
        MaterialTheme(colorScheme = colors, typography = typography(), shapes = AppShapes) {
            // Text and icons outside a Surface default to black; give every screen the theme's colours.
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                Box(Modifier.fillMaxSize().background(colors.surface)) { content() }
            }
        }
    }
}

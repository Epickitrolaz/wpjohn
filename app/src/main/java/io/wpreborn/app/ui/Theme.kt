package io.wpreborn.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Ink = Color(0xFF070B16)
val Night = Color(0xFF0B1020)
val SurfaceNight = Color(0xFF121A2D)
val SurfaceNightHigh = Color(0xFF1A2440)
val Periwinkle = Color(0xFF8AA4FF)
val Gold = Color(0xFFF5C86B)
val Mist = Color(0xFFE8EDF7)
val Muted = Color(0xFF8C97B4)

private val DarkScheme = darkColorScheme(
    primary = Periwinkle,
    onPrimary = Color(0xFF08122E),
    primaryContainer = Color(0xFF25335C),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Gold,
    onSecondary = Color(0xFF2A1E00),
    secondaryContainer = Color(0xFF3D2E0A),
    onSecondaryContainer = Color(0xFFFFE7B2),
    tertiary = Color(0xFF7BD8C0),
    background = Night,
    onBackground = Mist,
    surface = SurfaceNight,
    onSurface = Mist,
    surfaceVariant = SurfaceNightHigh,
    onSurfaceVariant = Color(0xFFB4BED6),
    outline = Color(0xFF34405E),
    outlineVariant = Color(0xFF222C46),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF3A0000),
    scrim = Color(0xCC000000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3358D4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF00164E),
    secondary = Color(0xFF8A6512),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7B2),
    onSecondaryContainer = Color(0xFF2A1E00),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF101426),
    surface = Color.White,
    onSurface = Color(0xFF101426),
    surfaceVariant = Color(0xFFEDF0F8),
    onSurfaceVariant = Color(0xFF495267),
    outline = Color(0xFFC4CAD9),
    outlineVariant = Color(0xFFE3E7F2),
    error = Color(0xFFB3261E),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, letterSpacing = (-0.5).sp, lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, letterSpacing = 0.6.sp,
    ),
)

@Composable
fun WpTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

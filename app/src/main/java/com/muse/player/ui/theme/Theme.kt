package com.muse.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.muse.player.R
import com.muse.player.data.AppSettings
import com.muse.player.data.ThemeMode

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun TextStyle.p(size: Int? = null, weight: FontWeight? = null, spacing: Float? = null) = copy(
    fontFamily = Poppins,
    fontSize = size?.sp ?: fontSize,
    fontWeight = weight ?: fontWeight,
    letterSpacing = spacing?.sp ?: letterSpacing,
)

private val base = Typography()
val MuseTypography = Typography(
    displayLarge = base.displayLarge.p(weight = FontWeight.Bold),
    displayMedium = base.displayMedium.p(weight = FontWeight.Bold),
    displaySmall = base.displaySmall.p(weight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.p(30, FontWeight.Bold, -0.6f),
    headlineMedium = base.headlineMedium.p(24, FontWeight.Bold, -0.4f),
    headlineSmall = base.headlineSmall.p(20, FontWeight.SemiBold, -0.2f),
    titleLarge = base.titleLarge.p(20, FontWeight.SemiBold, -0.2f),
    titleMedium = base.titleMedium.p(16, FontWeight.SemiBold, 0f),
    titleSmall = base.titleSmall.p(14, FontWeight.Medium, 0f),
    bodyLarge = base.bodyLarge.p(15, FontWeight.Normal, 0f),
    bodyMedium = base.bodyMedium.p(13, FontWeight.Normal, 0f),
    bodySmall = base.bodySmall.p(12, FontWeight.Normal, 0f),
    labelLarge = base.labelLarge.p(13, FontWeight.Medium, 0f),
    labelMedium = base.labelMedium.p(12, FontWeight.Medium, 0.2f),
    labelSmall = base.labelSmall.p(10, FontWeight.Medium, 0.2f),
)

val AccentPresets: List<Int> = listOf(
    0xFFFA2D48, 0xFFFF375F, 0xFFFF6B3D, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158,
    0xFF2EC4B6, 0xFF64D2FF, 0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFAC8E68,
).map { it.toInt() }

data class ThemePreset(val name: String, val mode: ThemeMode, val amoled: Boolean, val accent: Int)

val ThemePresets = listOf(
    ThemePreset("Classic", ThemeMode.SYSTEM, false, 0xFFFA2D48.toInt()),
    ThemePreset("Midnight", ThemeMode.DARK, true, 0xFFBF5AF2.toInt()),
    ThemePreset("Ocean", ThemeMode.DARK, false, 0xFF0A84FF.toInt()),
    ThemePreset("Paper", ThemeMode.LIGHT, false, 0xFF1C1C1E.toInt()),
    ThemePreset("Sunset", ThemeMode.DARK, false, 0xFFFF9F0A.toInt()),
    ThemePreset("Forest", ThemeMode.DARK, true, 0xFF30D158.toInt()),
    ThemePreset("Blossom", ThemeMode.LIGHT, false, 0xFFFF375F.toInt()),
    ThemePreset("Mono", ThemeMode.DARK, true, 0xFFF2F2F7.toInt()),
)

fun museColorScheme(dark: Boolean, amoled: Boolean, rawAccent: Color): ColorScheme {
    // Keep the accent readable against the background.
    val accent = when {
        dark && rawAccent.luminance() < 0.04f -> Color(0xFFF2F2F7)
        !dark && rawAccent.luminance() > 0.8f -> Color(0xFF1C1C1E)
        else -> rawAccent
    }
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    return if (dark) {
        val bg = if (amoled) Color.Black else Color(0xFF0C0C0E)
        val s1 = if (amoled) Color(0xFF0E0E0E) else Color(0xFF161618)
        val s2 = if (amoled) Color(0xFF181818) else Color(0xFF212124)
        val s3 = if (amoled) Color(0xFF232323) else Color(0xFF2C2C30)
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.22f).compositeOver(bg), onPrimaryContainer = accent,
            secondary = accent, onSecondary = onAccent, tertiary = accent,
            background = bg, onBackground = Color.White,
            surface = bg, onSurface = Color.White,
            surfaceVariant = s2, onSurfaceVariant = Color(0xFF9A9AA1),
            surfaceContainerLowest = bg, surfaceContainerLow = s1, surfaceContainer = s1,
            surfaceContainerHigh = s2, surfaceContainerHighest = s3,
            outline = Color(0xFF3A3A3C), outlineVariant = Color(0xFF262629),
            surfaceTint = Color.Transparent,
        )
    } else {
        val bg = Color.White
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.14f).compositeOver(bg), onPrimaryContainer = accent,
            secondary = accent, onSecondary = onAccent, tertiary = accent,
            background = bg, onBackground = Color(0xFF111113),
            surface = bg, onSurface = Color(0xFF111113),
            surfaceVariant = Color(0xFFEFEFF3), onSurfaceVariant = Color(0xFF86868B),
            surfaceContainerLowest = bg, surfaceContainerLow = Color(0xFFF7F7F9), surfaceContainer = Color(0xFFF2F2F6),
            surfaceContainerHigh = Color(0xFFEBEBF0), surfaceContainerHighest = Color(0xFFE3E3E8),
            outline = Color(0xFFC6C6C8), outlineVariant = Color(0xFFE5E5EA),
            surfaceTint = Color.Transparent,
        )
    }
}

@Composable
fun AppSettings.isDark(): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MuseTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val scheme = museColorScheme(settings.isDark(), settings.amoled, Color(settings.accent))
    MaterialTheme(colorScheme = scheme, typography = MuseTypography) {
        // Nothing sits inside a Surface at the root, so without this every Text/Icon falls back to black.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

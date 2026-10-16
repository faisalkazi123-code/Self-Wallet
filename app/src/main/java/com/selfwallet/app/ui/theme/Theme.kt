package com.selfwallet.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfwallet.app.data.prefs.ThemeMode

val Teal = Color(0xFF0F766E)
val TealBright = Color(0xFF14B8A6)
val Ink = Color(0xFF0F4C5C)
val ExpenseRed = Color(0xFFE11D48)
val IncomeGreen = Color(0xFF059669)
val CorporateBlue = Color(0xFF2563EB)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF3B6470),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEFF1),
    onSecondaryContainer = Color(0xFF0B2A31),
    tertiary = CorporateBlue,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF101C1B),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF101C1B),
    surfaceVariant = Color(0xFFE2ECEA),
    onSurfaceVariant = Color(0xFF4A5B59),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F6F5),
    surfaceContainer = Color(0xFFEAF2F1),
    surfaceContainerHigh = Color(0xFFE4EDEC),
    surfaceContainerHighest = Color(0xFFDEE8E6),
    outline = Color(0xFF7A8B89),
    outlineVariant = Color(0xFFC9D6D4),
    error = ExpenseRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0F4F49),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFA9CDD6),
    onSecondary = Color(0xFF0B3640),
    secondaryContainer = Color(0xFF244B55),
    onSecondaryContainer = Color(0xFFDDEFF1),
    tertiary = Color(0xFF93C5FD),
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF0B1413),
    onBackground = Color(0xFFE0EBE9),
    surface = Color(0xFF0B1413),
    onSurface = Color(0xFFE0EBE9),
    surfaceVariant = Color(0xFF26332F),
    onSurfaceVariant = Color(0xFFB6C6C3),
    surfaceContainerLowest = Color(0xFF070E0D),
    surfaceContainerLow = Color(0xFF111C1B),
    surfaceContainer = Color(0xFF152120),
    surfaceContainerHigh = Color(0xFF1C2928),
    surfaceContainerHighest = Color(0xFF243231),
    outline = Color(0xFF84948F),
    outlineVariant = Color(0xFF34433F),
    error = Color(0xFFFB7185),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val AmountStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-1).sp)

@Composable
fun isAppInDarkTheme(themeMode: String): Boolean = when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun SelfWalletTheme(
    themeMode: String = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(themeMode)
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}

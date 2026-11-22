package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PosExtendedColors(
    val dispatchGreen: Color,
    val dispatchGreenContainer: Color,
    val dispatchGreenOnContainer: Color,
    val returnRed: Color,
    val returnRedContainer: Color,
    val returnRedOnContainer: Color,
    val pendingAmber: Color,
    val pendingAmberContainer: Color,
    val borderSubtle: Color,
    val surfaceElevated: Color
)

private val LightExtendedColors = PosExtendedColors(
    dispatchGreen = EmeraldDispatch,
    dispatchGreenContainer = EmeraldDispatchContainer,
    dispatchGreenOnContainer = EmeraldDispatchOnContainer,
    returnRed = TerracottaReturn,
    returnRedContainer = TerracottaReturnContainer,
    returnRedOnContainer = TerracottaReturnOnContainer,
    pendingAmber = AmberPending,
    pendingAmberContainer = AmberPendingContainer,
    borderSubtle = WarmBorder,
    surfaceElevated = PaperCard
)

private val DarkExtendedColors = PosExtendedColors(
    dispatchGreen = EmeraldDarkTheme,
    dispatchGreenContainer = Color(0xFF143B28),
    dispatchGreenOnContainer = Color(0xFFB5F2D2),
    returnRed = TerracottaDarkTheme,
    returnRedContainer = Color(0xFF471B18),
    returnRedOnContainer = Color(0xFFFFCFCB),
    pendingAmber = CremaGoldDarkTheme,
    pendingAmberContainer = Color(0xFF3E2B11),
    borderSubtle = DarkRoastBorder,
    surfaceElevated = DarkRoastSurfaceAlt
)

val LocalPosColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = EspressoDark,
    onPrimary = Color.White,
    primaryContainer = CremaGoldLight,
    onPrimaryContainer = EspressoDeep,
    secondary = CremaGold,
    onSecondary = EspressoDeep,
    secondaryContainer = PaperWarmAlt,
    onSecondaryContainer = InkPrimary,
    tertiary = EmeraldDispatch,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldDispatchContainer,
    onTertiaryContainer = EmeraldDispatchOnContainer,
    error = TerracottaReturn,
    onError = Color.White,
    errorContainer = TerracottaReturnContainer,
    onErrorContainer = TerracottaReturnOnContainer,
    background = PaperCream,
    onBackground = InkPrimary,
    surface = PaperCard,
    onSurface = InkPrimary,
    surfaceVariant = PaperWarmAlt,
    onSurfaceVariant = InkSecondary,
    outline = WarmBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = CremaGoldDarkTheme,
    onPrimary = EspressoDeep,
    primaryContainer = EspressoSoft,
    onPrimaryContainer = DarkPaperInk,
    secondary = CremaGoldDarkTheme,
    onSecondary = EspressoDeep,
    secondaryContainer = DarkRoastSurfaceAlt,
    onSecondaryContainer = DarkPaperInk,
    tertiary = EmeraldDarkTheme,
    onTertiary = Color(0xFF062917),
    tertiaryContainer = Color(0xFF143B28),
    onTertiaryContainer = Color(0xFFB5F2D2),
    error = TerracottaDarkTheme,
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF471B18),
    onErrorContainer = Color(0xFFFFCFCB),
    background = DarkRoastBg,
    onBackground = DarkPaperInk,
    surface = DarkRoastSurface,
    onSurface = DarkPaperInk,
    surfaceVariant = DarkRoastSurfaceAlt,
    onSurfaceVariant = DarkInkMuted,
    outline = DarkRoastBorder
)

@Composable
fun BunaPosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val posColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalPosColors provides posColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

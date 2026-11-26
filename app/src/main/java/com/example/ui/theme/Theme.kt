package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class LedgerExtendedColors(
    val debt: Color,
    val debtContainer: Color,
    val onDebtContainer: Color,
    val settled: Color,
    val settledContainer: Color,
    val onSettledContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warningBorder: Color,
    val settledBorder: Color,
    val debtBorder: Color,
    val whatsapp: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val cardBorder: Color
)

val LocalLedgerColors = staticCompositionLocalOf {
    LedgerExtendedColors(
        debt = TerracottaDebt,
        debtContainer = TerracottaContainer,
        onDebtContainer = OnTerracottaContainer,
        settled = EmeraldVibrant,
        settledContainer = EmeraldContainer,
        onSettledContainer = OnEmeraldContainer,
        warning = SemaphoreYellow,
        warningContainer = SemaphoreYellowBg,
        onWarningContainer = OnAmberContainer,
        warningBorder = SemaphoreYellowBorder,
        settledBorder = SemaphoreGreenBorder,
        debtBorder = SemaphoreRedBorder,
        whatsapp = WhatsAppGreen,
        heroStart = EmeraldPrimary,
        heroEnd = EmeraldVibrant,
        cardBorder = WarmOutline
    )
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmerald,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = AmberSecondary,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = TerracottaDebt,
    onTertiary = Color.White,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = OnTerracottaContainer,
    background = CreamBackground,
    onBackground = InkText,
    surface = CardWhite,
    onSurface = InkText,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = MutedInkText,
    outline = WarmOutline,
    outlineVariant = WarmOutline,
    error = TerracottaDebt,
    onError = Color.White,
    errorContainer = TerracottaContainer,
    onErrorContainer = OnTerracottaContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = DarkOnEmerald,
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = DarkOnEmeraldContainer,
    secondary = DarkAmberSecondary,
    onSecondary = DarkOnAmber,
    secondaryContainer = DarkAmberContainer,
    onSecondaryContainer = DarkOnAmberContainer,
    tertiary = DarkTerracottaDebt,
    onTertiary = Color(0xFF450B00),
    tertiaryContainer = DarkTerracottaContainer,
    onTertiaryContainer = DarkOnTerracottaContainer,
    background = DarkBackground,
    onBackground = DarkInkText,
    surface = DarkSurface,
    onSurface = DarkInkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedInkText,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkTerracottaDebt,
    onError = Color(0xFF450B00),
    errorContainer = DarkTerracottaContainer,
    onErrorContainer = DarkOnTerracottaContainer
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MiLibretaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        LedgerExtendedColors(
            debt = DarkTerracottaDebt,
            debtContainer = DarkTerracottaContainer,
            onDebtContainer = DarkOnTerracottaContainer,
            settled = DarkEmeraldPrimary,
            settledContainer = DarkEmeraldContainer,
            onSettledContainer = DarkOnEmeraldContainer,
            warning = DarkAmberSecondary,
            warningContainer = DarkAmberContainer,
            onWarningContainer = DarkOnAmberContainer,
            warningBorder = Color(0xFF8A5900),
            settledBorder = Color(0xFF1F6B4E),
            debtBorder = Color(0xFF8F3523),
            whatsapp = Color(0xFF22C55E),
            heroStart = Color(0xFF0E3D2C),
            heroEnd = Color(0xFF165B42),
            cardBorder = DarkOutline
        )
    } else {
        LedgerExtendedColors(
            debt = TerracottaDebt,
            debtContainer = SemaphoreRedBg,
            onDebtContainer = OnTerracottaContainer,
            settled = SemaphoreGreen,
            settledContainer = SemaphoreGreenBg,
            onSettledContainer = OnEmeraldContainer,
            warning = SemaphoreYellow,
            warningContainer = SemaphoreYellowBg,
            onWarningContainer = OnAmberContainer,
            warningBorder = SemaphoreYellowBorder,
            settledBorder = SemaphoreGreenBorder,
            debtBorder = SemaphoreRedBorder,
            whatsapp = WhatsAppGreen,
            heroStart = EmeraldPrimary,
            heroEnd = EmeraldVibrant,
            cardBorder = WarmOutline
        )
    }

    CompositionLocalProvider(LocalLedgerColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

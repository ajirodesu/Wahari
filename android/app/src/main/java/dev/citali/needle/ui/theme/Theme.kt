package dev.citali.needle.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val WahariDarkColors = darkColorScheme(
    primary = WahariTokens.accent,
    onPrimary = Color.White,
    primaryContainer = WahariTokens.userBubble,
    onPrimaryContainer = Color.White,
    secondary = WahariTokens.accent,
    onSecondary = Color.White,
    secondaryContainer = WahariTokens.bgCard,
    onSecondaryContainer = WahariTokens.textPrimary,
    tertiary = WahariTokens.textMuted,
    onTertiary = WahariTokens.textPrimary,
    background = WahariTokens.bgMain,
    onBackground = WahariTokens.textPrimary,
    surface = WahariTokens.bgSurface,
    onSurface = WahariTokens.textPrimary,
    surfaceVariant = WahariTokens.bgCard,
    onSurfaceVariant = WahariTokens.textMuted,
    surfaceContainer = WahariTokens.bgSurface,
    surfaceContainerHigh = WahariTokens.bgCard,
    outline = WahariTokens.borderSubtle,
    outlineVariant = WahariTokens.borderCapsule,
    error = WahariTokens.danger,
    onError = Color.White,
    errorContainer = WahariTokens.bgCard,
    onErrorContainer = WahariTokens.danger,
)

private val WahariShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Dark-only theme. There is no light scheme and no dynamic color.
 * The system font scale is clamped to 1.3 and layout is forced LTR.
 */
@Composable
fun NeedleTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val clamped = Density(
        density = density.density,
        fontScale = minOf(density.fontScale, 1.3f),
    )
    CompositionLocalProvider(
        LocalDensity provides clamped,
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        MaterialTheme(
            colorScheme = WahariDarkColors,
            typography = Typography(),
            shapes = WahariShapes,
            content = content,
        )
    }
}

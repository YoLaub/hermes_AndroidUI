package com.example.hermes.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class HermesSpacing(
    val xs: Dp = 4.dp, val s: Dp = 8.dp, val m: Dp = 12.dp,
    val l: Dp = 16.dp, val xl: Dp = 24.dp, val xxl: Dp = 32.dp,
    val minTouch: Dp = 48.dp, val topBar: Dp = 64.dp, val bottomBar: Dp = 80.dp,
)

/** Roles Material 3 has no slot for. */
@Immutable
data class HermesExtraColors(
    val success: Color = HermesColors.Success,
    val successContainer: Color = HermesColors.SuccessContainer,
    val onSuccessContainer: Color = HermesColors.OnSuccessContainer,
    val warning: Color = HermesColors.Warning,
    val warningContainer: Color = HermesColors.WarningContainer,
    val onWarningContainer: Color = HermesColors.OnWarningContainer,
    val dangerFill: Color = HermesColors.DangerFill,
    val meta: Color = HermesColors.Meta,
    val code: Color = HermesColors.Code,
)

val LocalHermesSpacing = staticCompositionLocalOf { HermesSpacing() }
val LocalHermesColors = staticCompositionLocalOf { HermesExtraColors() }

val HermesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), // fields
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // cards
    large = RoundedCornerShape(20.dp), // sheets, dialogs
    extraLarge = RoundedCornerShape(28.dp),
)

private val HermesDarkScheme = darkColorScheme(
    primary = HermesColors.Primary,
    onPrimary = HermesColors.OnPrimary,
    primaryContainer = HermesColors.PrimaryContainer,
    onPrimaryContainer = HermesColors.OnPrimaryContainer,
    secondary = HermesColors.Success,
    onSecondary = HermesColors.OnPrimary,
    secondaryContainer = HermesColors.SuccessContainer,
    onSecondaryContainer = HermesColors.OnSuccessContainer,
    tertiary = HermesColors.Tertiary,
    onTertiary = HermesColors.OnPrimary,
    tertiaryContainer = HermesColors.TertiaryContainer,
    background = HermesColors.Background,
    onBackground = HermesColors.OnSurface,
    surface = HermesColors.Background,
    onSurface = HermesColors.OnSurface,
    surfaceVariant = HermesColors.SurfaceVariant,
    onSurfaceVariant = HermesColors.OnSurfaceVariant,
    surfaceContainerLowest = HermesColors.Background,
    surfaceContainerLow = HermesColors.Surface,
    surfaceContainer = HermesColors.Surface,
    surfaceContainerHigh = HermesColors.SurfaceVariant,
    surfaceContainerHighest = HermesColors.SurfaceVariant,
    outline = HermesColors.OutlineStrong,
    outlineVariant = HermesColors.Outline,
    error = HermesColors.Danger,
    onError = HermesColors.OnPrimary,
    errorContainer = HermesColors.DangerContainer,
    onErrorContainer = HermesColors.OnDangerContainer,
)

/** Dark only: the redesign defines a single scheme. */
@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHermesSpacing provides HermesSpacing(),
        LocalHermesColors provides HermesExtraColors(),
    ) {
        MaterialTheme(
            colorScheme = HermesDarkScheme,
            typography = HermesTypography,
            shapes = HermesShapes,
            content = content,
        )
    }
}

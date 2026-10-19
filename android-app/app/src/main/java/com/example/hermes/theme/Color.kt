package com.example.hermes.theme

import androidx.compose.ui.graphics.Color

/** Design tokens of the UX redesign (design&refonte/design/tokens.json). */
object HermesColors {
    val Background = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val SurfaceVariant = Color(0xFF21262D)
    val Outline = Color(0xFF30363D)
    val OutlineStrong = Color(0xFF484F58)
    val OnSurface = Color(0xFFE6EDF3)
    val OnSurfaceVariant = Color(0xFF9DA7B3)
    val Meta = Color(0xFF8B949E)
    val Disabled = Color(0xFF6E7681)
    val Primary = Color(0xFF58A6FF)
    val OnPrimary = Color(0xFF0D1117)
    val PrimaryContainer = Color(0xFF0C2D4F)
    val OnPrimaryContainer = Color(0xFFA5D6FF)
    val Success = Color(0xFF3FB950)
    val SuccessContainer = Color(0xFF0F2E1A)
    val OnSuccessContainer = Color(0xFF7EE787)
    val Tertiary = Color(0xFFBC8CFF)
    val TertiaryContainer = Color(0xFF2A1B47)
    val Warning = Color(0xFFD29922)
    val WarningContainer = Color(0xFF3A2A0B)
    val OnWarningContainer = Color(0xFFF2CC60)
    val Danger = Color(0xFFF85149)
    val DangerContainer = Color(0xFF3D1418)
    val OnDangerContainer = Color(0xFFFFA198)
    val DangerFill = Color(0xFFB62324) // destructive button, white text
    val Code = Color(0xFFA5D6FF)
    val AvatarPalette = listOf(
        Color(0xFF58A6FF), Color(0xFF39C5CF), Color(0xFF3FB950), Color(0xFFD29922),
        Color(0xFFFF9B5C), Color(0xFFF778BA), Color(0xFFBC8CFF), Color(0xFF9DA7B3),
    )
}

// Legacy names still used by the screens, mapped onto the new tokens.
val OnyxDarkBackground = HermesColors.Background
val OnyxDarkSurface = HermesColors.Surface
val OnyxDarkSurfaceVariant = HermesColors.SurfaceVariant
val OnyxBorder = HermesColors.Outline

val HermesPrimary = HermesColors.Primary
val HermesPrimaryContainer = HermesColors.PrimaryContainer
val HermesOnPrimaryContainer = HermesColors.OnPrimaryContainer

val HermesSecondary = HermesColors.OnSuccessContainer
val HermesSecondaryContainer = HermesColors.SuccessContainer
val HermesOnSecondaryContainer = HermesColors.OnSuccessContainer
val HermesSuccess = HermesColors.Success

val HermesTertiary = HermesColors.Tertiary
val HermesTertiaryContainer = HermesColors.TertiaryContainer

val HermesError = HermesColors.Danger
val HermesErrorContainer = HermesColors.DangerContainer

val HermesWarning = HermesColors.Warning
val HermesWarningContainer = HermesColors.WarningContainer

val HermesTextPrimary = HermesColors.OnSurface
val HermesTextSecondary = HermesColors.OnSurfaceVariant
// Was #6E7681, under AA on the background; metadata now uses the `meta` token.
val HermesTextMuted = HermesColors.Meta

val HermesToolBackground = Color(0xFF1A1F29)
val HermesCodeBackground = Color(0xFF0F141C)
val HermesUserBubble = HermesColors.PrimaryContainer
val HermesAssistantBubble = HermesColors.Surface

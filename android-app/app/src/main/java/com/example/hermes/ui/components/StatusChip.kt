package com.example.hermes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesColors

enum class StatusTone { Neutral, Info, Success, Warning, Danger, ReadOnly }

private data class ChipColors(val container: Color, val content: Color, val icon: ImageVector?)

private fun chipColors(tone: StatusTone) = when (tone) {
    StatusTone.Neutral -> ChipColors(HermesColors.SurfaceVariant, HermesColors.OnSurface, null)
    StatusTone.Info -> ChipColors(HermesColors.PrimaryContainer, HermesColors.OnPrimaryContainer, Icons.Filled.Info)
    StatusTone.Success -> ChipColors(HermesColors.SuccessContainer, HermesColors.OnSuccessContainer, Icons.Filled.Check)
    StatusTone.Warning -> ChipColors(HermesColors.WarningContainer, HermesColors.OnWarningContainer, Icons.Filled.WarningAmber)
    StatusTone.Danger -> ChipColors(HermesColors.DangerContainer, HermesColors.OnDangerContainer, Icons.Filled.Block)
    StatusTone.ReadOnly -> ChipColors(Color.Transparent, Color(0xFFC9D1D9), Icons.Filled.Lock)
}

/**
 * A state pill: always colour + icon + text, never colour alone. [ReadOnly][StatusTone.ReadOnly]
 * has a dashed border and a padlock (e.g. "secrets · Terminal uniquement").
 */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, tone: StatusTone = StatusTone.Neutral, icon: ImageVector? = null) {
    val colors = chipColors(tone)
    val shape = RoundedCornerShape(50)
    val border = when (tone) {
        StatusTone.Neutral -> Modifier.border(1.dp, HermesColors.Outline, shape)
        StatusTone.ReadOnly -> Modifier.drawBehind {
            drawRoundRect(
                color = HermesColors.Disabled,
                cornerRadius = CornerRadius(size.height / 2),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
            )
        }
        else -> Modifier
    }
    Row(
        modifier
            .height(28.dp)
            .background(colors.container, shape)
            .then(border)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val shown = icon ?: colors.icon
        if (shown != null) Icon(shown, contentDescription = null, tint = colors.content, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.content, maxLines = 1)
    }
}

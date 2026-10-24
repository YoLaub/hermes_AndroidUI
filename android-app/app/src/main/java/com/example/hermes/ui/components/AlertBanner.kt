package com.example.hermes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesColors

enum class AlertTone { Info, Warning, Blocking }

/**
 * Inline banner. [title] is bold and runs into [text] ("Avertissement. Auto-approbation active.").
 * The global control-session banner is [SessionBanner], not a tone of this one.
 */
@Composable
fun AlertBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: AlertTone = AlertTone.Info,
    title: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (container, content, defaultIcon) = when (tone) {
        AlertTone.Info -> Triple(HermesColors.PrimaryContainer, HermesColors.OnPrimaryContainer, Icons.Filled.Info)
        AlertTone.Warning -> Triple(HermesColors.WarningContainer, HermesColors.OnWarningContainer, Icons.Filled.Shield)
        AlertTone.Blocking -> Triple(HermesColors.DangerContainer, HermesColors.OnDangerContainer, Icons.Filled.WarningAmber)
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon ?: defaultIcon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(
            buildAnnotatedString {
                if (title != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                    append(" ")
                }
                append(text)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            BannerActionButton(actionLabel, onAction, border = if (tone == AlertTone.Warning) HermesColors.Warning else content, content = content)
        }
    }
}

@Composable
internal fun BannerActionButton(label: String, onClick: () -> Unit, border: Color, content: Color) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

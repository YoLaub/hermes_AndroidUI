package com.example.hermes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesColors

/**
 * A list line, 56 dp minimum. Give it [onClick] for a navigating row (chevron shown), or
 * [checked]/[onCheckedChange] for a switch row; [trailing] replaces the default end slot.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = HermesColors.OnSurfaceVariant,
    icon: ImageVector? = null,
    badge: Int = 0,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = when {
        !enabled -> Modifier
        checked != null && onCheckedChange != null ->
            Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) }
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (subtitle != null) 64.dp else 56.dp)
            .then(interaction)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = HermesColors.OnSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = HermesColors.OnSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        when {
            trailing != null -> trailing()
            checked != null -> Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = HermesColors.Primary, checkedThumbColor = HermesColors.Background),
            )
            else -> {
                if (badge > 0) CountBadge(badge)
                if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HermesColors.Meta, modifier = Modifier.size(18.dp))
            }
        }
    }
}

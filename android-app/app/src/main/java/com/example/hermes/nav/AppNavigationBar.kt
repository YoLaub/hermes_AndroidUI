package com.example.hermes.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.CountBadge
import com.example.hermes.ui.components.DotBadge

private fun iconOf(tab: Tab): ImageVector = when (tab) {
    Tab.Chat -> Icons.AutoMirrored.Outlined.Chat
    Tab.Inbox -> Icons.Outlined.Inbox
    Tab.Space -> Icons.Outlined.GridView
    Tab.Control -> Icons.Outlined.PhoneAndroid
    Tab.Agent -> Icons.Outlined.SmartToy
}

@Composable
private fun labelOf(tab: Tab): String = stringResource(
    when (tab) {
        Tab.Chat -> R.string.nav_chat
        Tab.Inbox -> R.string.nav_inbox
        Tab.Space -> R.string.nav_space
        Tab.Control -> R.string.nav_control
        Tab.Agent -> R.string.nav_agent
    }
)

/**
 * Bottom bar, five destinations. [inboxCount] is the red "waiting for a decision" total;
 * [controlLive] puts the orange dot on Contrôle while a session runs.
 */
@Composable
fun AppNavigationBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    inboxCount: Int = 0,
    controlLive: Boolean = false,
) {
    NavigationBar(containerColor = HermesColors.Surface) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val label = labelOf(tab)
            val spoken = when {
                tab == Tab.Inbox && inboxCount > 0 -> pluralStringResource(R.plurals.nav_inbox_a11y, inboxCount, inboxCount)
                tab == Tab.Control && controlLive -> stringResource(R.string.nav_control_live_a11y)
                tab == Tab.Control -> stringResource(R.string.nav_control_a11y)
                else -> label
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    // The icon carries the spoken label; the visible label is hidden from TalkBack.
                    Box(Modifier.semantics { contentDescription = spoken }) {
                        Icon(iconOf(tab), contentDescription = null)
                        if (tab == Tab.Inbox && inboxCount > 0) {
                            CountBadge(inboxCount, Modifier.align(Alignment.TopStart).offset(x = 14.dp, y = (-8).dp))
                        }
                        if (tab == Tab.Control && controlLive) {
                            DotBadge(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp), color = HermesColors.Warning)
                        }
                    }
                },
                label = {
                    Text(
                        label,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = HermesColors.OnSurface,
                    selectedTextColor = HermesColors.OnSurface,
                    unselectedIconColor = HermesColors.OnSurfaceVariant,
                    unselectedTextColor = HermesColors.OnSurfaceVariant,
                    indicatorColor = HermesColors.PrimaryContainer,
                ),
            )
        }
    }
}

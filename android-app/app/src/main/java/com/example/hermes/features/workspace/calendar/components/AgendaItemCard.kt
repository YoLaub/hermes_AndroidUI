package com.example.hermes.features.workspace.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.features.workspace.calendar.AgendaItem
import com.example.hermes.features.workspace.calendar.AgentRun
import com.example.hermes.features.workspace.calendar.PhoneEvent
import com.example.hermes.theme.*
import java.time.format.DateTimeFormatter

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** An event of the phone's calendar (editable when the app may write it) or an agent's scheduled run. */
@Composable
fun AgendaItemCard(
    item: AgendaItem,
    profileName: (String) -> String,
    onEdit: (PhoneEvent) -> Unit,
    onDelete: (PhoneEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val editable = item is PhoneEvent && item.editable
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .then(if (editable) Modifier.clickable { onEdit(item as PhoneEvent) } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (item is AgentRun) HermesTertiary.copy(alpha = 0.4f) else HermesSecondary.copy(alpha = 0.4f))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tag(
                    icon = Icons.Default.Schedule,
                    text = when (item) {
                        is PhoneEvent -> if (item.allDay) stringResource(R.string.agenda_all_day)
                            else "${item.start.format(HH_MM)} - ${item.end.format(HH_MM)}"
                        is AgentRun -> item.start.format(HH_MM)
                    },
                    color = HermesPrimary,
                    background = HermesCodeBackground,
                    monospace = true
                )
                if (item is AgentRun) {
                    Tag(Icons.Default.SmartToy, profileName(item.profile), HermesTertiary, HermesTertiaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = HermesTextPrimary
            )

            val detail = when (item) {
                is PhoneEvent -> item.location
                is AgentRun -> item.schedule?.let { stringResource(R.string.agenda_run_schedule, it) }
            }
            if (detail != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item is AgentRun) Icons.Default.Repeat else Icons.Default.Place,
                        contentDescription = null,
                        tint = HermesTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = detail, style = MaterialTheme.typography.labelSmall, color = HermesTextMuted)
                }
            }

            if (item is PhoneEvent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editable) "" else stringResource(R.string.agenda_read_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = HermesTextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    if (editable) {
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = HermesTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(icon: ImageVector, text: String, color: Color, background: Color, monospace: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

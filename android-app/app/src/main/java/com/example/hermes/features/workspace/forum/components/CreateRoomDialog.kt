package com.example.hermes.features.workspace.forum.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.R
import com.example.hermes.theme.*
import com.example.hermes.ui.components.Persona

/** A new room: a name, a topic, and the profiles that will answer in it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateRoomDialog(
    personas: List<Persona>,
    onCreate: (name: String, topic: String, icon: String, members: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("🏛️") }
    var members by remember { mutableStateOf(emptyList<String>()) }
    val iconOptions = listOf("🏛️", "🚀", "💡", "🛠️", "🎯", "📊")
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = HermesPrimary,
        unfocusedBorderColor = OnyxBorder,
        focusedLabelColor = HermesPrimary
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(OnyxBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.forum_new_room),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = HermesTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    iconOptions.forEach { emoji ->
                        FilterChip(
                            selected = selectedIcon == emoji,
                            onClick = { selectedIcon = emoji },
                            label = { Text(emoji, fontSize = 16.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HermesPrimaryContainer)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.forum_room_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.forum_room_topic)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = stringResource(R.string.forum_room_members), style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    personas.forEach { persona ->
                        val selected = persona.id in members
                        FilterChip(
                            selected = selected,
                            onClick = { members = if (selected) members - persona.id else members + persona.id },
                            label = { Text("🤖 ${persona.name}", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesTertiaryContainer,
                                selectedLabelColor = HermesTertiary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = stringResource(R.string.forum_room_cost), style = MaterialTheme.typography.bodySmall, color = HermesTextMuted)

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel), color = HermesTextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(name, topic, selectedIcon, members) },
                        enabled = name.isNotBlank() && members.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.forum_create), color = OnyxDarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

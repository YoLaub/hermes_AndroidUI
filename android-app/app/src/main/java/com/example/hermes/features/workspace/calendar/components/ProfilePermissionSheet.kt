package com.example.hermes.features.workspace.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.CalendarPermissionLevel
import com.example.hermes.core.model.ProfileCalendarPermission
import com.example.hermes.theme.*

@Composable
fun ProfilePermissionSheet(
    permissions: List<ProfileCalendarPermission>,
    onUpdatePermission: (profileName: String, level: CalendarPermissionLevel) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HermesTertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = HermesTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Droits du Calendrier",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Text(
                                text = "Permissions de lecture et écriture par agent",
                                style = MaterialTheme.typography.labelSmall,
                                color = HermesTextMuted
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = HermesTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(permissions) { perm ->
                        val isUser = perm.role == "user"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = HermesCodeBackground),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(if (isUser) HermesPrimaryContainer else HermesTertiaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
                                                contentDescription = null,
                                                tint = if (isUser) HermesPrimary else HermesTertiary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = perm.profileName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = HermesTextPrimary
                                        )
                                    }

                                    // Role Tag
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(OnyxDarkSurfaceVariant)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isUser) "Utilisateur" else "Agent",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HermesTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Permission Selector Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CalendarPermissionLevel.entries.forEach { level ->
                                        val isSelected = perm.permission == level
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onUpdatePermission(perm.profileName, level) },
                                            label = {
                                                Text(
                                                    text = when (level) {
                                                        CalendarPermissionLevel.ADMIN -> "Admin"
                                                        CalendarPermissionLevel.READ_WRITE -> "Lecture & Écriture"
                                                        CalendarPermissionLevel.READ_ONLY -> "Lecture seule"
                                                    },
                                                    fontSize = 11.sp
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = when (level) {
                                                    CalendarPermissionLevel.ADMIN -> HermesPrimaryContainer
                                                    CalendarPermissionLevel.READ_WRITE -> HermesSecondaryContainer
                                                    CalendarPermissionLevel.READ_ONLY -> OnyxDarkSurfaceVariant
                                                },
                                                selectedLabelColor = when (level) {
                                                    CalendarPermissionLevel.ADMIN -> HermesPrimary
                                                    CalendarPermissionLevel.READ_WRITE -> HermesSecondary
                                                    CalendarPermissionLevel.READ_ONLY -> HermesTextSecondary
                                                }
                                            ),
                                            modifier = Modifier.height(30.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Terminé", color = OnyxDarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

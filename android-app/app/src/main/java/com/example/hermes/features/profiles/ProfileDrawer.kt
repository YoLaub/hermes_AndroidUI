package com.example.hermes.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ProfileInfo
import com.example.hermes.theme.*

@Composable
fun ProfileDrawerContent(
    profiles: List<ProfileInfo>,
    activeProfile: String,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onOpenEnv: () -> Unit,
    onOpenOpenbao: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var profileToDelete by remember { mutableStateOf<String?>(null) }

    ModalDrawerSheet(
        modifier = modifier.width(330.dp),
        drawerContainerColor = OnyxDarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with Create Profile Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(HermesPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = HermesPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Profils Agent",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                }

                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onCreateProfile()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nouveau", fontSize = 12.sp)
                }
            }

            Text(
                text = "Basculez de persona, de configuration et d'environnement",
                style = MaterialTheme.typography.bodySmall,
                color = HermesTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            HorizontalDivider(color = OnyxBorder, modifier = Modifier.padding(vertical = 4.dp))

            // Profile List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles) { profile ->
                    val isCurrent = profile.name == activeProfile

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectProfile(profile.name)
                                onDismiss()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) HermesPrimaryContainer.copy(alpha = 0.4f) else OnyxDarkSurfaceVariant
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isCurrent) HermesPrimary else OnyxBorder
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isCurrent) HermesPrimary else OnyxDarkBackground)
                                    .border(1.dp, if (isCurrent) HermesPrimary else OnyxBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.name.take(2).uppercase(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCurrent) Color.Black else HermesTextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.name.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = HermesTextPrimary
                                    )
                                    if (profile.isDefault) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = OnyxDarkBackground
                                        ) {
                                            Text(
                                                text = "défaut",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = HermesTextMuted,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = profile.model ?: "Modèle par défaut",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HermesTextSecondary,
                                    maxLines = 1
                                )
                                if (profile.skillCount > 0) {
                                    Text(
                                        text = "${profile.skillCount} skills actifs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HermesSecondary
                                    )
                                }
                            }

                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Actif",
                                    tint = HermesPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (!profile.isDefault) {
                                IconButton(
                                    onClick = { profileToDelete = profile.name },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Supprimer profil",
                                        tint = HermesTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = OnyxBorder, modifier = Modifier.padding(vertical = 8.dp))

            // Profile Tools Section (Skills, Memory, Workspaces, Env)
            Text(
                text = "GESTION DU PROFIL ACTIF ($activeProfile)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = HermesTextMuted,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            // 2x2 action buttons grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Skills Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenSkills()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesSecondary)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Skills", fontSize = 11.sp)
                    }

                    // Memory Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenMemory()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesPrimary)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mémoire", fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Workspaces Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenWorkspaces()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesTextPrimary)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dossiers", fontSize = 11.sp)
                    }

                    // Env Variables Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenEnv()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24))
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Variables .env", fontSize = 11.sp)
                    }
                }

                // OpenBao Vault Button
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenOpenbao()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = HermesSecondaryContainer.copy(alpha = 0.25f),
                        contentColor = HermesSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HermesSecondary.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = HermesSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Coffre OpenBao (Secrets)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Confirmation dialog for profile deletion
    profileToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Supprimer le profil") },
            text = {
                Text("Êtes-vous sûr de vouloir supprimer le profil \"$name\" ? Cette action effacera son dossier de configuration et ses variables d'environnement.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProfile(name)
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Annuler")
                }
            },
            containerColor = OnyxDarkSurface
        )
    }
}

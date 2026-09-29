package com.example.hermes.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.theme.*

@Composable
fun SettingsDialog(
    serverUrl: String,
    activeProfile: String,
    sessionId: String?,
    yoloEnabled: Boolean,
    onToggleYolo: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenWorkspaces: () -> Unit,
    onOpenProfiles: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, OnyxBorder, RoundedCornerShape(20.dp)),
            color = OnyxDarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(HermesPrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = HermesPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Paramètres & Outils",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Text(
                                text = "Configuration globale et session active",
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = HermesTextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    color = OnyxBorder,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── 1. MODE YOLO SECTION ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (yoloEnabled) HermesPrimaryContainer.copy(alpha = 0.35f) else OnyxDarkSurfaceVariant
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (yoloEnabled) HermesPrimary else OnyxBorder
                            )
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (yoloEnabled) HermesPrimary else OnyxDarkBackground
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = if (yoloEnabled) Color.Black else HermesPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Mode YOLO",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = HermesTextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = if (yoloEnabled) HermesPrimary else OnyxDarkBackground,
                                                shape = RoundedCornerShape(6.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (yoloEnabled) HermesPrimary else OnyxBorder
                                                )
                                            ) {
                                                Text(
                                                    text = if (yoloEnabled) "ACTIF" else "DÉSACTIVÉ",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (yoloEnabled) Color.Black else HermesTextMuted,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Auto-approbation des commandes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HermesTextSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = yoloEnabled,
                                    onCheckedChange = { onToggleYolo() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = HermesPrimary,
                                        uncheckedThumbColor = HermesTextMuted,
                                        uncheckedTrackColor = OnyxDarkBackground
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (yoloEnabled) {
                                    "⚡ En mode YOLO, toutes les commandes bash et exécutions d'outils s'exécutent immédiatement sans vous demander de confirmation manuelle."
                                } else {
                                    "🛡️ En mode normal, chaque commande risquée ou outil critique demandera votre accord explicite avant son exécution."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (yoloEnabled) HermesPrimary else HermesTextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // ── 2. AGENT TOOLS & KNOWLEDGE SECTION ──
                    Text(
                        text = "OUTILS & GESTION DE L'AGENT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextMuted,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // Skills Item
                    SettingsActionItem(
                        icon = Icons.Default.Build,
                        iconTint = HermesSecondary,
                        iconBackground = HermesSecondaryContainer,
                        title = "Skills (Compétences)",
                        subtitle = "Inspecter, lire et supprimer les skills chargées",
                        badge = "Tools",
                        onClick = onOpenSkills
                    )

                    // Memory Item
                    SettingsActionItem(
                        icon = Icons.Default.Description,
                        iconTint = HermesPrimary,
                        iconBackground = HermesPrimaryContainer,
                        title = "Mémoire & Directives",
                        subtitle = "Consulter et éditer MEMORY.md, USER.md, SOUL.md",
                        badge = "Wiki",
                        onClick = onOpenMemory
                    )

                    // Workspaces Item
                    SettingsActionItem(
                        icon = Icons.Default.Folder,
                        iconTint = HermesTextPrimary,
                        iconBackground = OnyxDarkSurfaceVariant,
                        title = "Espaces de travail (Workspaces)",
                        subtitle = "Gérer les répertoires et dossiers autorisés",
                        badge = "FS",
                        onClick = onOpenWorkspaces
                    )

                    // Profile Switcher Item
                    SettingsActionItem(
                        icon = Icons.Default.SmartToy,
                        iconTint = HermesSecondary,
                        iconBackground = HermesSecondaryContainer.copy(alpha = 0.5f),
                        title = "Profil Actif : ${activeProfile.replaceFirstChar { it.uppercase() }}",
                        subtitle = "Changer de persona, modèle d'IA et environnement",
                        badge = "Profil",
                        onClick = onOpenProfiles
                    )

                    // ── 3. INSTANCE & CONNECTION SECTION ──
                    Text(
                        text = "CONNEXION & SERVEUR",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = OnyxDarkSurfaceVariant),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(HermesSecondary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connecté à l'instance",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = HermesSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                color = HermesTextPrimary
                            )

                            if (sessionId != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Session active : ${sessionId.take(12)}...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = HermesTextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OnyxDarkBackground)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = HermesSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mot de passe mémorisé localement",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HermesTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Disconnect / Change server button
                            OutlinedButton(
                                onClick = { showDisconnectConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesError),
                                border = androidx.compose.foundation.BorderStroke(1.dp, HermesError.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Changer de serveur / Déconnexion")
                            }
                        }
                    }

                    // ── 4. APP INFO ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hermes Mobile • WebUI Client v0.51",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextMuted
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog before disconnect
    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = {
                Text(
                    "Déconnexion du serveur",
                    color = HermesTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Voulez-vous retourner à l'écran de configuration du serveur ? Votre mot de passe reste sauvegardé pour faciliter la reconnexion.",
                    color = HermesTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirm = false
                        onDismiss()
                        onDisconnect()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HermesError)
                ) {
                    Text("Se déconnecter", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Annuler", color = HermesTextSecondary)
                }
            },
            containerColor = OnyxDarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OnyxDarkSurfaceVariant),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = HermesTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = OnyxDarkBackground,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OnyxBorder)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = HermesTextMuted,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HermesTextSecondary,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = HermesTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

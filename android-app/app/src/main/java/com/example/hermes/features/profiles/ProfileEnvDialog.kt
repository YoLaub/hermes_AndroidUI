package com.example.hermes.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ProviderInfo
import com.example.hermes.theme.*

@Composable
fun ProfileEnvDialog(
    activeProfile: String,
    providers: List<ProviderInfo>,
    isLoading: Boolean,
    onSaveKey: (providerId: String, apiKey: String) -> Unit,
    onDeleteKey: (providerId: String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    var selectedProviderForEdit by remember { mutableStateOf<ProviderInfo?>(null) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Variables d'environnement (.env)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    Text(
                        text = "Profil actif : $activeProfile",
                        style = MaterialTheme.typography.labelSmall,
                        color = HermesPrimary
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir", tint = HermesTextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                // Info banner explaining profile isolation
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OnyxDarkBackground,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = HermesSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Les clés sont stockées dans le .env étanche de ce profil ($activeProfile) et ne fuient pas vers les autres agents.",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextSecondary
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HermesPrimary)
                    }
                } else if (providers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Aucun provider détecté par le serveur",
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(providers, key = { it.id }) { provider ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = OnyxDarkBackground),
                                shape = RoundedCornerShape(10.dp),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = provider.displayName.ifBlank { provider.id.replaceFirstChar { it.uppercase() } },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = HermesTextPrimary
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (provider.hasKey) Color(0xFF10B981) else Color(0xFF6B7280))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (provider.hasKey) "Clé configurée (${provider.keySource})" else "Aucune clé",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (provider.hasKey) Color(0xFF10B981) else HermesTextMuted
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (provider.hasKey) {
                                            IconButton(
                                                onClick = { onDeleteKey(provider.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Supprimer clé",
                                                    tint = HermesTextMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                selectedProviderForEdit = provider
                                                apiKeyInput = ""
                                                showPassword = false
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(if (provider.hasKey) "Modifier" else "Ajouter", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
        containerColor = OnyxDarkSurface
    )

    // Edit key sub-dialog
    selectedProviderForEdit?.let { provider ->
        AlertDialog(
            onDismissRequest = { selectedProviderForEdit = null },
            title = {
                Text(
                    text = "Configurer la clé ${provider.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = HermesTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Cette variable sera écrite dans le .env du profil $activeProfile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Clé d'API") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = HermesTextMuted
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OnyxDarkBackground,
                            unfocusedContainerColor = OnyxDarkBackground,
                            focusedBorderColor = HermesPrimary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            onSaveKey(provider.id, apiKeyInput.trim())
                            selectedProviderForEdit = null
                        }
                    },
                    enabled = apiKeyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
                ) {
                    Text("Enregistrer dans .env")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProviderForEdit = null }) {
                    Text("Annuler")
                }
            },
            containerColor = OnyxDarkSurface
        )
    }
}

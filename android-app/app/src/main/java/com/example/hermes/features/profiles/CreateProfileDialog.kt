package com.example.hermes.features.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ProfileInfo
import com.example.hermes.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileDialog(
    existingProfiles: List<ProfileInfo>,
    onDismiss: () -> Unit,
    onCreate: (name: String, cloneFrom: String?, model: String?, provider: String?, apiKey: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedCloneFrom by remember { mutableStateOf(existingProfiles.firstOrNull()?.name ?: "default") }
    var defaultModel by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    val isNameValid = remember(name) {
        name.isNotBlank() && name.matches(Regex("^[a-z0-9][a-z0-9_-]{0,63}$"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Créer un nouveau profil",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = HermesTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.lowercase().trim() },
                    label = { Text("Nom du profil *") },
                    placeholder = { Text("ex: coder, redacteur, french") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (name.isNotBlank() && !isNameValid) "Lettres minuscules, chiffres, tirets uniquement"
                            else "Identifiant unique du profil",
                            fontSize = 11.sp,
                            color = if (name.isNotBlank() && !isNameValid) HermesWarning else HermesTextMuted
                        )
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

                // Clone from selector
                Text(
                    text = "Cloner la configuration de base :",
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesTextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    existingProfiles.take(4).forEach { p ->
                        val isSelected = selectedCloneFrom == p.name
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCloneFrom = p.name },
                            label = { Text(p.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = HermesPrimary
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text("Modèle par défaut (optionnel)") },
                    placeholder = { Text("ex: anthropic/claude-3-5-sonnet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OnyxDarkBackground,
                        unfocusedContainerColor = OnyxDarkBackground,
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder
                    )
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Clé d'API (optionnel, sauvé dans son .env)") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
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
                    if (isNameValid) {
                        onCreate(
                            name,
                            selectedCloneFrom.ifBlank { null },
                            defaultModel.ifBlank { null },
                            provider.ifBlank { null },
                            apiKey.ifBlank { null }
                        )
                    }
                },
                enabled = isNameValid,
                colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Créer le profil")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
        containerColor = OnyxDarkSurface
    )
}

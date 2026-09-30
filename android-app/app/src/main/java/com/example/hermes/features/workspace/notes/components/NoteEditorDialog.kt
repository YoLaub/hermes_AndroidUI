package com.example.hermes.features.workspace.notes.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.AgentNote
import com.example.hermes.theme.*

@Composable
fun NoteEditorDialog(
    note: AgentNote,
    onSave: (title: String, content: String, author: String, tags: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var author by remember { mutableStateOf(note.author) }
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(note.tags) }

    val authorOptions = listOf("Yoann", "Mario", "Gaston", "Hermes")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (note.title.isBlank() && note.content.isBlank()) "Nouvelle Note" else "Modifier la Note",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = HermesTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Author selection chips
                Text(
                    text = "Auteur / Profil :",
                    style = MaterialTheme.typography.labelMedium,
                    color = HermesTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    authorOptions.forEach { opt ->
                        FilterChip(
                            selected = author == opt,
                            onClick = { author = opt },
                            label = { Text(opt, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (opt == "Yoann") HermesPrimaryContainer else HermesTertiaryContainer,
                                selectedLabelColor = if (opt == "Yoann") HermesPrimary else HermesTertiary
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de la note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder,
                        focusedLabelColor = HermesPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Content Input
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Contenu (Markdown supporté)") },
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HermesPrimary,
                        unfocusedBorderColor = OnyxBorder,
                        focusedLabelColor = HermesPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tags Input & List
                Text(
                    text = "Tags :",
                    style = MaterialTheme.typography.labelMedium,
                    color = HermesTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        placeholder = { Text("Ex: Prospection, Client...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesPrimary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = tagInput.trim().removePrefix("#")
                            if (trimmed.isNotBlank() && !tags.contains(trimmed)) {
                                tags = tags + trimmed
                                tagInput = ""
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(HermesPrimaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter tag",
                            tint = HermesPrimary
                        )
                    }
                }

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { t ->
                            InputChip(
                                selected = false,
                                onClick = { tags = tags.filter { it != t } },
                                label = { Text("#$t", fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Supprimer",
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = OnyxDarkSurfaceVariant,
                                    labelColor = HermesPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = HermesTextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(title, content, author, tags) },
                        colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer", color = OnyxDarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

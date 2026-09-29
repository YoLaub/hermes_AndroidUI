package com.example.hermes.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.MemoryResponse
import com.example.hermes.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDialog(
    profileName: String,
    memoryData: MemoryResponse?,
    isLoading: Boolean,
    onSaveSection: (section: String, content: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: memory, 1: user, 2: soul
    val tabs = listOf("MEMORY.md", "USER.md", "SOUL.md")

    var memoryText by remember(memoryData?.memory) { mutableStateOf(memoryData?.memory ?: "") }
    var userText by remember(memoryData?.user) { mutableStateOf(memoryData?.user ?: "") }
    var soulText by remember(memoryData?.soul) { mutableStateOf(memoryData?.soul ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = OnyxDarkSurface,
            tonalElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = HermesPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Mémoire & Âme",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Text(
                                text = "Profil: $profileName",
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = HermesTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs (MEMORY / USER / SOUL)
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = OnyxDarkSurfaceVariant,
                    contentColor = HermesPrimary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HermesPrimary)
                    }
                } else {
                    val currentText = when (selectedTab) {
                        0 -> memoryText
                        1 -> userText
                        else -> soulText
                    }
                    val hint = when (selectedTab) {
                        0 -> "Notes et mémoire persistante de l'agent..."
                        1 -> "Préférences utilisateur (USER.md)..."
                        else -> "Personnalité et directives système de l'agent (SOUL.md)..."
                    }

                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { newText ->
                            when (selectedTab) {
                                0 -> memoryText = newText
                                1 -> userText = newText
                                2 -> soulText = newText
                            }
                        },
                        placeholder = { Text(hint, color = HermesTextMuted) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = HermesTextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OnyxDarkBackground,
                            unfocusedContainerColor = OnyxDarkBackground,
                            focusedBorderColor = HermesPrimary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Fermer", color = HermesTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val section = when (selectedTab) {
                                0 -> "memory"
                                1 -> "user"
                                else -> "soul"
                            }
                            val content = when (selectedTab) {
                                0 -> memoryText
                                1 -> userText
                                else -> soulText
                            }
                            onSaveSection(section, content)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrer", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

package com.example.hermes.features.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.SessionSummary
import com.example.hermes.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsDrawerContent(
    sessions: List<SessionSummary>,
    currentSessionId: String?,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onDismiss: () -> Unit,
    showAllProfiles: Boolean = false,
    otherProfileCount: Int = 0,
    onToggleAllProfiles: () -> Unit = {},
    searchResults: List<SessionSummary>? = null,
    isSearchingSessions: Boolean = false,
    onSearchServer: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    isRepairingSessions: Boolean = false,
    onRepairSessions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val displaySessions = remember(sessions, searchResults, searchQuery) {
        if (searchResults != null) {
            searchResults
        } else if (searchQuery.isBlank()) {
            sessions
        } else {
            sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalDrawerSheet(
        modifier = modifier.width(330.dp),
        drawerContainerColor = OnyxDarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with New Chat Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Conversations",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = HermesTextPrimary
                )

                FilledTonalButton(
                    onClick = {
                        onNewSession()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nouveau")
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (searchResults != null) {
                        onClearSearch()
                    }
                },
                placeholder = { Text("Rechercher...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = HermesTextMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty() || searchResults != null) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                onClearSearch()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = HermesTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = OnyxDarkBackground,
                    unfocusedContainerColor = OnyxDarkBackground,
                    focusedBorderColor = HermesPrimary,
                    unfocusedBorderColor = OnyxBorder
                )
            )

            // Filter Chips & Recovery Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showAllProfiles,
                    onClick = onToggleAllProfiles,
                    label = {
                        val extra = if (!showAllProfiles && otherProfileCount > 0) " (+$otherProfileCount)" else ""
                        Text(
                            text = if (showAllProfiles) "Tous profils" else "Profil actif$extra",
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showAllProfiles) Icons.Default.Public else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HermesPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = HermesPrimary,
                        selectedLeadingIconColor = HermesPrimary
                    )
                )

                FilledTonalIconButton(
                    onClick = onRepairSessions,
                    enabled = !isRepairingSessions,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = OnyxDarkSurfaceVariant
                    )
                ) {
                    if (isRepairingSessions) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = HermesPrimary
                        )
                    } else {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = "Récupérer les sessions",
                            tint = HermesTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (searchResults != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Résultats serveur (${searchResults.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = HermesSecondary
                    )
                    TextButton(
                        onClick = onClearSearch,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Réinitialiser", fontSize = 11.sp, color = HermesPrimary)
                    }
                }
            }

            HorizontalDivider(color = OnyxBorder, modifier = Modifier.padding(vertical = 6.dp))

            // Session List
            if (displaySessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Aucune session trouvée",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HermesTextMuted
                        )
                        if (searchQuery.isNotBlank() && searchResults == null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { onSearchServer(searchQuery) },
                                enabled = !isSearchingSessions,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isSearchingSessions) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Chercher sur le serveur", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displaySessions, key = { it.sessionId }) { session ->
                        val isSelected = session.sessionId == currentSessionId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectSession(session.sessionId)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) HermesPrimaryContainer.copy(alpha = 0.5f) else OnyxDarkSurfaceVariant
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) HermesPrimary else OnyxBorder
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (session.pinned) Icons.Default.PushPin else Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isSelected) HermesPrimary else HermesTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title.ifBlank { "Sans titre (${session.sessionId.take(8)})" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = HermesTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${session.messageCount} msg",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HermesTextMuted
                                        )
                                        session.profile?.let { prof ->
                                            Text(
                                                text = " • $prof",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HermesSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteSession(session.sessionId) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Supprimer",
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
    }
}

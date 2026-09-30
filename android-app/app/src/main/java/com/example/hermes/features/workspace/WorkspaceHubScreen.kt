package com.example.hermes.features.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.features.workspace.calendar.CalendarScreen
import com.example.hermes.features.workspace.calendar.CalendarViewModel
import com.example.hermes.features.workspace.notes.NotesScreen
import com.example.hermes.features.workspace.notes.NotesViewModel
import com.example.hermes.theme.*

enum class WorkspaceTab(val title: String, val icon: ImageVector) {
    NOTES("Notes & Audio", Icons.Default.Description),
    CALENDAR("Calendrier", Icons.Default.CalendarMonth),
    FORUM("Forum Agents", Icons.Default.Forum)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceHubScreen(
    notesViewModel: NotesViewModel,
    calendarViewModel: CalendarViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(WorkspaceTab.NOTES) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HermesPrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = HermesPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Zone Commune",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Text(
                                text = "Collaboration & Agents Hub",
                                style = MaterialTheme.typography.labelSmall,
                                color = HermesTextMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = HermesTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OnyxDarkSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = OnyxDarkSurface,
                tonalElevation = 8.dp
            ) {
                WorkspaceTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) HermesPrimary else HermesTextMuted
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                color = if (isSelected) HermesPrimary else HermesTextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = HermesPrimaryContainer
                        )
                    )
                }
            }
        },
        containerColor = OnyxDarkBackground
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                WorkspaceTab.NOTES -> {
                    NotesScreen(viewModel = notesViewModel)
                }
                WorkspaceTab.CALENDAR -> {
                    CalendarScreen(viewModel = calendarViewModel)
                }
                WorkspaceTab.FORUM -> {
                    // Placeholder for Forum Module (Step 3)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = null,
                                tint = HermesTertiary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "🏛️ Forum de Discussion Multi-Agents",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Salons collaboratifs entre vous et vos profils sélectionnés (Mario, Gaston, etc.) (Étape 3).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HermesTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

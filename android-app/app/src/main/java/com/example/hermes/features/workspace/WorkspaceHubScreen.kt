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
import androidx.compose.ui.res.stringResource
import com.example.hermes.R
import com.example.hermes.ui.components.Persona
import com.example.hermes.features.workspace.calendar.CalendarScreen
import com.example.hermes.features.workspace.calendar.CalendarViewModel
import com.example.hermes.features.workspace.forum.ForumScreen
import com.example.hermes.features.workspace.forum.ForumViewModel
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
    forumViewModel: ForumViewModel,
    personas: List<Persona> = emptyList(),
    onOpenTasks: () -> Unit = {},
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
                actions = {
                    // Tâches (Kanban) now lives in Espace; the hub redesign turns this into a card.
                    IconButton(onClick = onOpenTasks) {
                        Icon(
                            imageVector = Icons.Default.ViewKanban,
                            contentDescription = stringResource(R.string.space_tasks),
                            tint = HermesPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OnyxDarkSurface
                )
            )
        },
        containerColor = OnyxDarkBackground
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Sections as top tabs: the app's bottom bar now holds the five destinations.
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = OnyxDarkSurface,
                    contentColor = HermesPrimary
                ) {
                    WorkspaceTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.title, style = MaterialTheme.typography.labelLarge) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            selectedContentColor = HermesPrimary,
                            unselectedContentColor = HermesTextSecondary
                        )
                    }
                }
                when (selectedTab) {
                    WorkspaceTab.NOTES -> {
                        NotesScreen(viewModel = notesViewModel, personas = personas)
                    }
                    WorkspaceTab.CALENDAR -> {
                        CalendarScreen(viewModel = calendarViewModel, personas = personas)
                    }
                    WorkspaceTab.FORUM -> {
                        ForumScreen(viewModel = forumViewModel, personas = personas)
                    }
                }
            }
        }
    }
}

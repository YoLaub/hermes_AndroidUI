package com.example.hermes.features.workspace.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.features.workspace.calendar.components.EventCard
import com.example.hermes.features.workspace.calendar.components.EventEditorDialog
import com.example.hermes.features.workspace.calendar.components.MonthCalendarView
import com.example.hermes.features.workspace.calendar.components.ProfilePermissionSheet
import com.example.hermes.theme.*
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    val profileFilters = listOf(
        null to "🌐 Vue Globale",
        "Yoann" to "👤 Mon Agenda",
        "Mario" to "🤖 Mario",
        "Gaston" to "🤖 Gaston",
        "Hermes" to "🤖 Hermes"
    )

    val frenchDateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnyxDarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Profile / View Selector Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(profileFilters) { (profileKey, label) ->
                        val isSelected = state.selectedProfileFilter == profileKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onSelectProfileFilter(profileKey) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HermesPrimaryContainer,
                                selectedLabelColor = HermesPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Rights & Permissions Button
                IconButton(
                    onClick = { viewModel.openPermissionSheet() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Droits d'accès",
                        tint = HermesTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Month Calendar View
            MonthCalendarView(
                currentMonth = state.currentMonth,
                selectedDate = state.selectedDate,
                eventsMap = state.allEventsCountForMonth,
                onDateSelected = { viewModel.onSelectDate(it) },
                onMonthOffset = { viewModel.onMonthChanged(it) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Day Agenda Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.selectedDate.format(frenchDateFormatter).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = HermesTextPrimary
                )

                Text(
                    text = "${state.filteredEventsForSelectedDate.size} créneau(x)",
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesTextMuted
                )
            }

            // Day Events Timeline / List
            if (state.filteredEventsForSelectedDate.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = HermesTextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aucun créneau ce jour-ci",
                            style = MaterialTheme.typography.titleMedium,
                            color = HermesTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cliquez sur '+' pour planifier un rendez-vous ou une tâche d'agent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    items(state.filteredEventsForSelectedDate, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            onToggleStatus = { viewModel.toggleEventStatus(event.id) },
                            onEdit = { viewModel.openEditEvent(event) },
                            onDelete = { viewModel.deleteEvent(event.id) }
                        )
                    }
                }
            }
        }

        // Floating Action Button (New Event / Booking)
        ExtendedFloatingActionButton(
            onClick = { viewModel.openNewEvent() },
            containerColor = HermesPrimary,
            contentColor = OnyxDarkBackground,
            shape = RoundedCornerShape(16.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Créneau / RDV", fontWeight = FontWeight.Bold) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )

        // Event Editor Dialog
        if (state.isEventEditorOpen && state.activeEditingEvent != null) {
            EventEditorDialog(
                event = state.activeEditingEvent!!,
                onSave = { title, desc, date, start, end, org, assigned, st, loc ->
                    viewModel.saveEvent(title, desc, date, start, end, org, assigned, st, loc)
                },
                onDismiss = { viewModel.closeEventEditor() }
            )
        }

        // Permissions Sheet
        if (state.isPermissionSheetOpen) {
            ProfilePermissionSheet(
                permissions = state.permissions,
                onUpdatePermission = { prof, lvl ->
                    viewModel.updatePermission(prof, lvl)
                },
                onDismiss = { viewModel.closePermissionSheet() }
            )
        }
    }
}

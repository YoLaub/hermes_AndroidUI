package com.example.hermes.features.workspace.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.R
import com.example.hermes.features.workspace.calendar.components.AgendaItemCard
import com.example.hermes.features.workspace.calendar.components.EventEditorDialog
import com.example.hermes.features.workspace.calendar.components.MonthCalendarView
import com.example.hermes.theme.*
import com.example.hermes.ui.components.AppFab
import com.example.hermes.ui.components.Persona
import java.time.format.DateTimeFormatter
import java.util.*

private val CALENDAR_PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    personas: List<Persona> = emptyList(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    // The phone's calendar may have changed since the last visit.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refresh()
    }

    val profiles = (personas.map { it.id } + state.runProfiles).distinctBy { it.lowercase() }
    val filters = listOf<Pair<AgendaFilter, String>>(
        AgendaFilter.All to stringResource(R.string.agenda_filter_all),
        AgendaFilter.Phone to stringResource(R.string.agenda_filter_phone),
    ) + profiles.map { id -> AgendaFilter.Profile(id) to "🤖 " + (personas.firstOrNull { it.id == id }?.name ?: id) }

    val frenchDateFormatter = remember { DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnyxDarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { (filter, label) ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.onSelectFilter(filter) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HermesPrimaryContainer,
                            selectedLabelColor = HermesPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            if (!state.canReadPhone) {
                CalendarAccessCard(onAllow = { askCalendar.launch(CALENDAR_PERMISSIONS) })
            }
            if (state.runsUnavailable) {
                Notice(stringResource(R.string.agenda_runs_unavailable))
            }
            if (state.writeFailed) {
                Notice(stringResource(R.string.agenda_write_failed))
            }

            MonthCalendarView(
                currentMonth = state.currentMonth,
                selectedDate = state.selectedDate,
                markedDays = state.itemsByDay.keys,
                onDateSelected = { viewModel.onSelectDate(it) },
                onMonthOffset = { viewModel.onMonthChanged(it) }
            )

            Spacer(modifier = Modifier.height(6.dp))

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
                    text = stringResource(R.string.agenda_creneau_x, state.dayItems.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesTextMuted
                )
            }

            if (state.dayItems.isEmpty()) {
                // Bottom padding keeps the hint clear of the new-slot FAB, like the list's.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = HermesTextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.agenda_aucun_creneau_ce_jour_ci),
                            style = MaterialTheme.typography.titleMedium,
                            color = HermesTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.agenda_cliquez_sur_pour_planifier_un),
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextMuted,
                            textAlign = TextAlign.Center
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
                    items(state.dayItems, key = { it.key }) { item ->
                        AgendaItemCard(
                            item = item,
                            profileName = { id -> personas.firstOrNull { it.id == id }?.name ?: id },
                            onEdit = { viewModel.openEditEvent(it) },
                            onDelete = { viewModel.deleteEvent(it) }
                        )
                    }
                }
            }
        }

        AppFab(
            label = stringResource(R.string.agenda_new_slot),
            onClick = { if (state.canReadPhone) viewModel.openNewEvent() else askCalendar.launch(CALENDAR_PERMISSIONS) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )

        state.editor?.let { editor ->
            EventEditorDialog(
                editor = editor,
                onSave = { title, start, end, location -> viewModel.saveEvent(title, start, end, location) },
                onDismiss = { viewModel.closeEventEditor() }
            )
        }
    }
}

@Composable
private fun CalendarAccessCard(onAllow: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.agenda_allow_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = HermesTextPrimary
                )
                Text(
                    text = stringResource(R.string.agenda_allow_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = HermesTextSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(onClick = onAllow) { Text(stringResource(R.string.agenda_allow_button)) }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = HermesWarning,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

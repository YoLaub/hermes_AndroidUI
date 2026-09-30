package com.example.hermes.features.workspace.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.data.CalendarRepository
import com.example.hermes.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val events: List<CalendarEvent> = emptyList(),
    val filteredEventsForSelectedDate: List<CalendarEvent> = emptyList(),
    val allEventsCountForMonth: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val permissions: List<ProfileCalendarPermission> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedProfileFilter: String? = null, // null means "Vue Globale"
    val isEventEditorOpen: Boolean = false,
    val activeEditingEvent: CalendarEvent? = null,
    val isPermissionSheetOpen: Boolean = false,
    val isLoading: Boolean = false,
    val messageNotice: String? = null
)

class CalendarViewModel(
    private val repository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadCalendarData()
    }

    fun loadCalendarData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val (events, perms) = repository.loadData()
            _uiState.update {
                it.copy(
                    events = events,
                    permissions = perms,
                    isLoading = false
                )
            }
            updateFilteredEvents()
        }
    }

    fun onSelectDate(date: LocalDate) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                currentMonth = YearMonth.of(date.year, date.month)
            )
        }
        updateFilteredEvents()
    }

    fun onMonthChanged(offset: Int) {
        val nextMonth = _uiState.value.currentMonth.plusMonths(offset.toLong())
        _uiState.update { it.copy(currentMonth = nextMonth) }
    }

    fun onSelectProfileFilter(profile: String?) {
        _uiState.update { it.copy(selectedProfileFilter = if (it.selectedProfileFilter == profile) null else profile) }
        updateFilteredEvents()
    }

    fun openNewEvent(date: LocalDate = _uiState.value.selectedDate) {
        val newEvent = CalendarEvent(
            title = "",
            dateEpochDay = date.toEpochDay(),
            startTime = "09:00",
            endTime = "10:00",
            organizer = "Yoann",
            assignedProfiles = listOf("Yoann")
        )
        _uiState.update { it.copy(isEventEditorOpen = true, activeEditingEvent = newEvent) }
    }

    fun openEditEvent(event: CalendarEvent) {
        _uiState.update { it.copy(isEventEditorOpen = true, activeEditingEvent = event) }
    }

    fun closeEventEditor() {
        _uiState.update { it.copy(isEventEditorOpen = false, activeEditingEvent = null) }
    }

    fun saveEvent(
        title: String,
        description: String,
        date: LocalDate,
        startTime: String,
        endTime: String,
        organizer: String,
        assignedProfiles: List<String>,
        status: EventStatus,
        location: String
    ) {
        val current = _uiState.value.activeEditingEvent ?: return
        if (title.isBlank()) return

        val updated = current.copy(
            title = title,
            description = description,
            dateEpochDay = date.toEpochDay(),
            startTime = startTime,
            endTime = endTime,
            organizer = organizer,
            assignedProfiles = assignedProfiles.ifEmpty { listOf(organizer) },
            status = status,
            locationOrLink = location
        )

        viewModelScope.launch {
            repository.saveEvent(updated)
            loadCalendarData()
            closeEventEditor()
            _uiState.update { it.copy(messageNotice = "Événement enregistré") }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
            loadCalendarData()
        }
    }

    fun toggleEventStatus(eventId: String) {
        val event = _uiState.value.events.find { it.id == eventId } ?: return
        val nextStatus = when (event.status) {
            EventStatus.CONFIRMED -> EventStatus.COMPLETED
            EventStatus.COMPLETED -> EventStatus.CONFIRMED
            EventStatus.PENDING_AGENT -> EventStatus.CONFIRMED
            EventStatus.CANCELLED -> EventStatus.CONFIRMED
        }
        viewModelScope.launch {
            repository.saveEvent(event.copy(status = nextStatus))
            loadCalendarData()
        }
    }

    // --- Permissions Controls ---

    fun openPermissionSheet() {
        _uiState.update { it.copy(isPermissionSheetOpen = true) }
    }

    fun closePermissionSheet() {
        _uiState.update { it.copy(isPermissionSheetOpen = false) }
    }

    fun updatePermission(profileName: String, level: CalendarPermissionLevel) {
        val current = _uiState.value.permissions.toMutableList()
        val index = current.indexOfFirst { it.profileName == profileName }
        if (index != -1) {
            current[index] = current[index].copy(permission = level)
            viewModelScope.launch {
                repository.updatePermissions(current)
                _uiState.update { it.copy(permissions = current) }
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(messageNotice = null) }
    }

    private fun updateFilteredEvents() {
        val current = _uiState.value
        val filterProfile = current.selectedProfileFilter
        val selectedDateEpoch = current.selectedDate.toEpochDay()

        // 1. Group events by date for the month calendar view
        val monthEventsMap = current.events
            .filter { ev ->
                filterProfile == null || ev.organizer == filterProfile || ev.assignedProfiles.contains(filterProfile)
            }
            .groupBy { LocalDate.ofEpochDay(it.dateEpochDay) }

        // 2. Filter events for the currently selected date
        val forDate = current.events.filter { ev ->
            ev.dateEpochDay == selectedDateEpoch &&
                    (filterProfile == null || ev.organizer == filterProfile || ev.assignedProfiles.contains(filterProfile))
        }.sortedBy { it.startTime }

        _uiState.update {
            it.copy(
                allEventsCountForMonth = monthEventsMap,
                filteredEventsForSelectedDate = forDate
            )
        }
    }
}

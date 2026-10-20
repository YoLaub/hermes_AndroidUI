package com.example.hermes.features.workspace.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hermes.core.model.CronJob
import com.example.hermes.features.workspace.EventFormError
import com.example.hermes.features.workspace.eventFormError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the editor writes to the phone's calendar. Times are "HH:mm". */
data class EventDraft(val title: String, val date: LocalDate, val start: String, val end: String, val location: String)

/** The phone's own calendar, as the agenda reads and changes it. */
interface PhoneCalendar {
    fun canRead(): Boolean
    /** Events from [from] (included) to [until] (excluded). */
    suspend fun events(from: LocalDate, until: LocalDate): List<PhoneEvent>
    suspend fun create(draft: EventDraft): Boolean
    suspend fun update(eventId: Long, draft: EventDraft): Boolean
    suspend fun delete(eventId: Long): Boolean
}

/** The event being written: null [eventId] for a new one. */
data class EventEditorState(val eventId: Long?, val draft: EventDraft, val error: EventFormError? = null)

data class CalendarUiState(
    val items: List<AgendaItem> = emptyList(),
    val selectedDate: LocalDate,
    val currentMonth: YearMonth,
    val filter: AgendaFilter = AgendaFilter.All,
    val canReadPhone: Boolean = true,
    val runsUnavailable: Boolean = false,
    val writeFailed: Boolean = false,
    val editor: EventEditorState? = null,
) {
    private val visible get() = items.filteredBy(filter)
    val itemsByDay: Map<LocalDate, List<AgendaItem>> get() = visible.byDay()
    val dayItems: List<AgendaItem> get() = itemsByDay[selectedDate].orEmpty()
    /** Profiles that have a run this month, for the filter chips. */
    val runProfiles: List<String> get() = items.filterIsInstance<AgentRun>().map { it.profile }.distinct().sorted()
}

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

class CalendarViewModel(
    private val phone: PhoneCalendar,
    private val loadRuns: suspend () -> Result<List<CronJob>>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(today().let { CalendarUiState(selectedDate = it, currentMonth = YearMonth.from(it)) })
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Reads the shown month again: after a permission grant, a return to the screen, or a change. */
    fun refresh() {
        val month = _uiState.value.currentMonth
        viewModelScope.launch {
            val readable = phone.canRead()
            val events = if (readable) phone.events(month.atDay(1), month.plusMonths(1).atDay(1)) else emptyList()
            val runs = loadRuns()
            _uiState.update {
                it.copy(
                    items = events + agentRuns(runs.getOrDefault(emptyList()), zone),
                    canReadPhone = readable,
                    runsUnavailable = runs.isFailure,
                )
            }
        }
    }

    fun onSelectDate(date: LocalDate) {
        val month = YearMonth.from(date)
        val changed = month != _uiState.value.currentMonth
        _uiState.update { it.copy(selectedDate = date, currentMonth = month) }
        if (changed) refresh()
    }

    fun onMonthChanged(offset: Int) {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(offset.toLong())) }
        refresh()
    }

    fun onSelectFilter(filter: AgendaFilter) {
        _uiState.update { it.copy(filter = if (it.filter == filter) AgendaFilter.All else filter) }
    }

    fun openNewEvent() {
        val draft = EventDraft("", _uiState.value.selectedDate, "09:00", "10:00", "")
        _uiState.update { it.copy(editor = EventEditorState(null, draft)) }
    }

    fun openEditEvent(event: PhoneEvent) {
        val draft = EventDraft(event.title, event.start.toLocalDate(), event.start.format(HH_MM), event.end.format(HH_MM), event.location.orEmpty())
        _uiState.update { it.copy(editor = EventEditorState(event.eventId, draft)) }
    }

    fun closeEventEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    fun saveEvent(title: String, start: String, end: String, location: String) {
        val editor = _uiState.value.editor ?: return
        val draft = editor.draft.copy(title = title.trim(), start = start.trim(), end = end.trim(), location = location.trim())
        eventFormError(draft.title, draft.start, draft.end)?.let { error ->
            _uiState.update { it.copy(editor = editor.copy(draft = draft, error = error)) }
            return
        }
        viewModelScope.launch {
            val ok = editor.eventId?.let { phone.update(it, draft) } ?: phone.create(draft)
            _uiState.update { it.copy(editor = if (ok) null else editor.copy(draft = draft), writeFailed = !ok) }
            refresh()
        }
    }

    fun deleteEvent(event: PhoneEvent) {
        viewModelScope.launch {
            val ok = phone.delete(event.eventId)
            _uiState.update { it.copy(writeFailed = !ok) }
            refresh()
        }
    }
}

package com.example.hermes.features.workspace.calendar

import com.example.hermes.core.model.CronJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.parse("2026-10-20")

    private class FakePhone(var readable: Boolean = true) : PhoneCalendar {
        val events = mutableListOf<PhoneEvent>()
        val asked = mutableListOf<Pair<LocalDate, LocalDate>>()
        val created = mutableListOf<EventDraft>()
        val updated = mutableListOf<Pair<Long, EventDraft>>()
        val deleted = mutableListOf<Long>()
        override fun canRead() = readable
        override suspend fun events(from: LocalDate, until: LocalDate): List<PhoneEvent> {
            asked += from to until
            return events
        }
        override suspend fun create(draft: EventDraft) = true.also { created += draft }
        override suspend fun update(eventId: Long, draft: EventDraft) = true.also { updated += eventId to draft }
        override suspend fun delete(eventId: Long) = true.also { deleted += eventId }
    }

    private fun event(id: Long, title: String, at: String) =
        PhoneEvent(id, title, LocalDateTime.parse(at), LocalDateTime.parse(at).plusHours(1), null, false)

    private val job = CronJob(id = "j", name = "veille", nextRunAt = "2026-10-20T06:00:00+00:00", profile = "memtest")

    private fun viewModel(phone: PhoneCalendar, runs: Result<List<CronJob>> = Result.success(listOf(job))) =
        CalendarViewModel(phone, loadRuns = { runs }, zone = paris, today = { today })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the day shows the phone's events and the agents' runs of the month`() {
        val phone = FakePhone().apply { events += event(1, "Dentiste", "2026-10-20T09:30") }

        val state = viewModel(phone).uiState.value

        assertEquals(LocalDate.parse("2026-10-01") to LocalDate.parse("2026-11-01"), phone.asked.single())
        assertEquals(listOf("veille", "Dentiste"), state.dayItems.map { it.title })
        assertTrue(state.canReadPhone)
    }

    @Test
    fun `without calendar access the phone is not read and the runs still show`() {
        val phone = FakePhone(readable = false)

        val state = viewModel(phone).uiState.value

        assertTrue(phone.asked.isEmpty())
        assertFalse(state.canReadPhone)
        assertEquals(listOf("veille"), state.dayItems.map { it.title })
    }

    @Test
    fun `an unreachable server keeps the phone's events and says so`() {
        val phone = FakePhone().apply { events += event(1, "Dentiste", "2026-10-20T09:30") }

        val state = viewModel(phone, runs = Result.failure(IOException("offline"))).uiState.value

        assertTrue(state.runsUnavailable)
        assertEquals(listOf("Dentiste"), state.dayItems.map { it.title })
    }

    @Test
    fun `changing month reads that month`() {
        val phone = FakePhone()
        val vm = viewModel(phone)

        vm.onMonthChanged(1)

        assertEquals(LocalDate.parse("2026-11-01") to LocalDate.parse("2026-12-01"), phone.asked.last())
    }

    @Test
    fun `a filter keeps only the phone or one profile`() {
        val phone = FakePhone().apply { events += event(1, "Dentiste", "2026-10-20T09:30") }
        val vm = viewModel(phone)

        vm.onSelectFilter(AgendaFilter.Phone)
        assertEquals(listOf("Dentiste"), vm.uiState.value.dayItems.map { it.title })

        vm.onSelectFilter(AgendaFilter.Profile("memtest"))
        assertEquals(listOf("veille"), vm.uiState.value.dayItems.map { it.title })
    }

    @Test
    fun `a new slot is written to the phone's calendar`() {
        val phone = FakePhone()
        val vm = viewModel(phone)

        vm.openNewEvent()
        vm.saveEvent(title = "Appel banque", start = "14:00", end = "14:30", location = "")

        assertEquals(listOf(EventDraft("Appel banque", today, "14:00", "14:30", "")), phone.created)
        assertNull(vm.uiState.value.editor)
        assertEquals(2, phone.asked.size)
    }

    @Test
    fun `an invalid slot is not written and the editor says why`() {
        val phone = FakePhone()
        val vm = viewModel(phone)

        vm.openNewEvent()
        vm.saveEvent(title = "Appel", start = "15:00", end = "14:00", location = "")

        assertTrue(phone.created.isEmpty())
        assertNotNull(vm.uiState.value.editor?.error)
    }

    @Test
    fun `an event is changed or deleted in the phone's calendar`() {
        val dentist = event(7, "Dentiste", "2026-10-20T09:30")
        val phone = FakePhone().apply { events += dentist }
        val vm = viewModel(phone)

        vm.openEditEvent(dentist)
        assertEquals("09:30", vm.uiState.value.editor?.draft?.start)
        vm.saveEvent(title = "Dentiste", start = "10:00", end = "11:00", location = "Vannes")
        vm.deleteEvent(dentist)

        assertEquals(7L to EventDraft("Dentiste", today, "10:00", "11:00", "Vannes"), phone.updated.single())
        assertEquals(listOf(7L), phone.deleted)
    }
}

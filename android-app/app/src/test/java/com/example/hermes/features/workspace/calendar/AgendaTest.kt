package com.example.hermes.features.workspace.calendar

import com.example.hermes.core.mobilecontrol.RawCalendarRow
import com.example.hermes.core.model.CronJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The agenda shows the phone's calendar and the agents' next scheduled runs, never invented data. */
class AgendaTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun ms(text: String) = ZonedDateTime.of(LocalDateTime.parse(text), paris).toInstant().toEpochMilli()

    @Test
    fun `a phone event keeps its local times`() {
        val row = RawCalendarRow("Dentiste", ms("2026-10-20T09:30"), ms("2026-10-20T10:00"), "Vannes", false, 42)

        val event = phoneEvents(listOf(row), paris).single()

        assertEquals(42L, event.eventId)
        assertEquals(LocalDateTime.parse("2026-10-20T09:30"), event.start)
        assertEquals(LocalDateTime.parse("2026-10-20T10:00"), event.end)
        assertEquals("Vannes", event.location)
    }

    @Test
    fun `an all-day event stays on its day whatever the time zone`() {
        // The provider stores all-day events at UTC midnight.
        val start = LocalDate.parse("2026-10-21").atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val row = RawCalendarRow("Férié", start, start + 86_400_000, null, true, 7)

        val event = phoneEvents(listOf(row), ZoneId.of("America/New_York")).single()

        assertEquals(listOf(LocalDate.parse("2026-10-21")), event.days())
    }

    @Test
    fun `a scheduled job shows at its next run, in local time`() {
        val job = CronJob(id = "j1", name = "extraire-titre", nextRunAt = "2026-10-20T12:25:13+00:00", profile = "memtest")

        val run = agentRuns(listOf(job), paris).single()

        assertEquals(LocalDateTime.parse("2026-10-20T14:25:13"), run.start)
        assertEquals("extraire-titre", run.title)
        assertEquals("memtest", run.profile)
    }

    @Test
    fun `a job without a next run or a name is shown sensibly or not at all`() {
        val jobs = listOf(
            CronJob(id = "done", name = "fini", nextRunAt = null),
            CronJob(id = "noname", prompt = "Résume mes mails du jour", nextRunAt = "2026-10-21T06:00:00+00:00"),
        )

        val runs = agentRuns(jobs, paris)

        assertEquals(listOf("noname"), runs.map { it.jobId })
        assertEquals("Résume mes mails du jour", runs.single().title)
        assertEquals(DEFAULT_PROFILE, runs.single().profile)
    }

    @Test
    fun `filters keep the phone events, one profile's runs, or everything`() {
        val event = phoneEvents(listOf(RawCalendarRow("RDV", ms("2026-10-20T09:00"), ms("2026-10-20T10:00"), null, false, 1)), paris)
        val runs = agentRuns(
            listOf(
                CronJob(id = "a", name = "a", nextRunAt = "2026-10-20T08:00:00+00:00", profile = "memtest"),
                CronJob(id = "b", name = "b", nextRunAt = "2026-10-20T06:00:00+00:00"),
            ),
            paris,
        )
        val all = event + runs

        assertEquals(listOf("b", "RDV", "a"), all.filteredBy(AgendaFilter.All).map { it.title })
        assertEquals(listOf("RDV"), all.filteredBy(AgendaFilter.Phone).map { it.title })
        assertEquals(listOf("a"), all.filteredBy(AgendaFilter.Profile("memtest")).map { it.title })
    }

    @Test
    fun `an event over several days is listed on each of them`() {
        val row = RawCalendarRow("Salon", ms("2026-10-20T18:00"), ms("2026-10-22T10:00"), null, false, 3)

        val byDay = phoneEvents(listOf(row), paris).byDay()

        assertEquals(setOf("2026-10-20", "2026-10-21", "2026-10-22").map(LocalDate::parse).toSet(), byDay.keys)
        assertTrue(byDay.values.all { it.single().title == "Salon" })
    }
}

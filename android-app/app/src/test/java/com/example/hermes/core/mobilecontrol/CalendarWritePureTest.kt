package com.example.hermes.core.mobilecontrol

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class CalendarWritePureTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val paris = TimeZone.getTimeZone("Europe/Paris")
    private val now = 1791288000000L                    // 2026-10-06T12:00Z
    private val start = 1791385200000L                  // 2026-10-07T15:00Z
    private val end = 1791388800000L                    // 2026-10-07T16:00Z
    private val hour = 3_600_000L
    private val day = 24 * hour

    // ── Reading the agent's date and time ──

    @Test
    fun aLocalDateTimeIsReadInTheGivenZone() {
        assertEquals(start, CalendarWritePure.parseLocal("2026-10-07T15:00", utc))
        assertEquals(1791378000000L, CalendarWritePure.parseLocal("2026-10-07T15:00", paris))
        assertEquals(1791385230000L, CalendarWritePure.parseLocal("2026-10-07T15:00:30", utc))
    }

    @Test
    fun anythingThatIsNotExactlyThatShapeIsRejected() {
        for (bad in listOf(null, "", "  ", "2026-13-01T10:00", "2026-02-30T10:00", "2026-10-07 15:00",
            "07/10/2026 15:00", "2026-10-07T25:00", "2026-10-07T15:00zzz", "demain 15h", "2026-10-07")) {
            assertNull(bad, CalendarWritePure.parseLocal(bad, utc))
        }
    }

    // ── Creating ──

    private fun create(
        title: String? = "Dentiste", s: Long? = start, e: Long? = end, location: String? = "Cabinet"
    ) = CalendarWritePure.validateCreate(title, s, e, location, now)

    @Test
    fun aSensibleEventIsAccepted() {
        assertNull(create())
        assertNull(create(location = null))
    }

    @Test
    fun aTitleIsRequiredAndBounded() {
        assertEquals("INVALID_ARGUMENTS", create(title = null)!!.code)
        assertEquals("INVALID_ARGUMENTS", create(title = "   ")!!.code)
        assertNull(create(title = "t".repeat(200)))
        assertEquals("INVALID_ARGUMENTS", create(title = "t".repeat(201))!!.code)
    }

    @Test
    fun aLocationIsBounded() {
        assertNull(create(location = "l".repeat(200)))
        assertEquals("INVALID_ARGUMENTS", create(location = "l".repeat(201))!!.code)
    }

    @Test
    fun startAndEndAreRequiredAndEndMustFollowStart() {
        assertEquals("INVALID_ARGUMENTS", create(s = null)!!.code)
        assertEquals("INVALID_ARGUMENTS", create(e = null)!!.code)
        assertEquals("INVALID_ARGUMENTS", create(e = start)!!.code)
        assertEquals("INVALID_ARGUMENTS", create(e = start - hour)!!.code)
    }

    @Test
    fun anEventLastsAtMostTwentyFourHours() {
        assertNull(create(e = start + day))
        assertEquals("INVALID_ARGUMENTS", create(e = start + day + 1)!!.code)
    }

    @Test
    fun theStartMustBeWithinOneYearBackAndTwoYearsAhead() {
        assertNull(create(s = now - 365 * day, e = now - 365 * day + hour))
        assertEquals("INVALID_ARGUMENTS", create(s = now - 366 * day, e = now - 366 * day + hour)!!.code)
        assertNull(create(s = now + 730 * day, e = now + 730 * day + hour))
        assertEquals("INVALID_ARGUMENTS", create(s = now + 731 * day, e = now + 731 * day + hour)!!.code)
    }

    // ── Which existing events may be touched ──

    private fun facts(
        writable: Boolean = true, visible: Boolean = true, recurring: Boolean = false,
        attendees: Boolean = false, allDay: Boolean = false
    ) = EventFacts(
        title = "Réunion", startMs = start, endMs = end, location = "Salle 1", calendarName = "Perso",
        calendarWritable = writable, calendarVisible = visible, recurring = recurring,
        hasAttendees = attendees, allDay = allDay
    )

    @Test
    fun anOrdinaryEventInAWritableVisibleCalendarIsEditable() {
        assertNull(CalendarWritePure.editability(facts()))
    }

    @Test
    fun eachRiskyKindOfEventIsRefusedWithItsOwnReason() {
        val reasons = mapOf(
            "recurring" to CalendarWritePure.editability(facts(recurring = true)),
            "attendees" to CalendarWritePure.editability(facts(attendees = true)),
            "readonly" to CalendarWritePure.editability(facts(writable = false)),
            "hidden" to CalendarWritePure.editability(facts(visible = false)),
            "allday" to CalendarWritePure.editability(facts(allDay = true))
        )
        for ((name, p) in reasons) {
            assertEquals(name, "EVENT_NOT_EDITABLE", p!!.code)
        }
        assertEquals(5, reasons.values.map { it!!.message }.toSet().size)
    }

    // ── Modifying ──

    private fun update(
        title: String? = null, s: Long? = null, e: Long? = null, location: String? = null
    ) = CalendarWritePure.validateUpdate(title, s, e, location, facts(), now)

    @Test
    fun anUpdateMustChangeSomething() {
        assertEquals("INVALID_ARGUMENTS", update()!!.code)
        assertNull(update(title = "Nouveau titre"))
        assertNull(update(location = "Ailleurs"))
    }

    @Test
    fun aGivenTitleCannotBeBlankAndTheResultMustStayValid() {
        assertEquals("INVALID_ARGUMENTS", update(title = "  ")!!.code)
        assertNull(update(s = start + hour, e = end + hour))
        assertEquals("INVALID_ARGUMENTS", update(s = end + hour)!!.code)         // new start after the unchanged end
        assertEquals("INVALID_ARGUMENTS", update(e = start + day + hour)!!.code)  // would last more than 24 h
    }

    // ── What the user is shown before tapping ──

    @Test
    fun theCreateConfirmationShowsTitleTimesPlaceAndCalendar() {
        val t = CalendarWritePure.createText("Dentiste", start, end, "Cabinet", "Perso", utc)
        assertEquals("Créer cet événement ?", t.headline)
        assertTrue(t.details.contains("Dentiste"))
        assertTrue(t.details.contains("2026-10-07 15:00"))
        assertTrue(t.details.contains("16:00"))
        assertTrue(t.details.contains("Cabinet"))
        assertTrue(t.details.contains("Perso"))
        assertFalse(CalendarWritePure.createText("Dentiste", start, end, null, "Perso", utc).details.contains("Lieu"))
        // Real line breaks, not the two characters backslash and n.
        assertEquals(4, t.details.lines().size)
        assertFalse(t.details.contains("\\n"))
    }

    @Test
    fun theUpdateConfirmationShowsOnlyWhatChangesWithOldAndNew() {
        val t = CalendarWritePure.updateText(facts(), newTitle = "Réunion équipe", newStartMs = null, newEndMs = null,
            newLocation = null, zone = utc)
        assertEquals("Modifier cet événement ?", t.headline)
        assertTrue(t.details.contains("Réunion") && t.details.contains("Réunion équipe"))
        assertFalse(t.details.contains("Salle 1"))
        val moved = CalendarWritePure.updateText(facts(), null, start + hour, end + hour, null, utc)
        assertTrue(moved.details.contains("2026-10-07 15:00") && moved.details.contains("2026-10-07 16:00"))
    }

    @Test
    fun theDeleteConfirmationShowsTheEventThatWillDisappear() {
        val t = CalendarWritePure.deleteText(facts(), utc)
        assertEquals("Supprimer cet événement ?", t.headline)
        assertTrue(t.details.contains("Réunion") && t.details.contains("2026-10-07 15:00") && t.details.contains("Salle 1"))
    }

    // ── No line breaks or control characters in what the user reads on the confirmation ──

    @Test
    fun aTitleOrPlaceWithAControlCharacterIsRefusedOnCreate() {
        for (bad in listOf("a\nb", "a\rb", "a\tb", "a\u0000b", "a\u007fb", "a\u2028b", "a\u2029b")) {
            assertEquals(bad, "INVALID_ARGUMENTS", create(title = bad)!!.code)
            assertEquals(bad, "INVALID_ARGUMENTS", create(location = bad)!!.code)
        }
    }

    @Test
    fun theSameHoldsOnUpdateButAnEmptyLocationStillClearsIt() {
        assertEquals("INVALID_ARGUMENTS", update(title = "x\ny")!!.code)
        assertEquals("INVALID_ARGUMENTS", update(location = "x\ny")!!.code)
        assertNull(update(location = ""))
    }

    @Test
    fun ordinaryAccentsPunctuationAndSpacesAreStillFine() {
        assertNull(create(title = "Rendez-vous chez Zoë — 15 h (bureau) #2", location = "12, rue de l'Église"))
    }
}

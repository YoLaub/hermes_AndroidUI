package com.example.hermes.features.guard

import com.example.hermes.core.model.MemoryProfile
import com.example.hermes.features.profiles.ADMIN_ONLY_ZONES
import com.example.hermes.features.profiles.TOGGLE_ZONES

/** The four fixed tabs of « Mémoire & garde ». */
enum class GuardTab { Pending, Actions, Profiles, Mandates }

data class ZoneRow(val zone: String, val read: Boolean, val write: Boolean)

/** One row per zone the app may toggle, most sensitive first. */
fun zoneGrid(read: Set<String>, write: Set<String>): List<ZoneRow> =
    TOGGLE_ZONES.map { ZoneRow(it, read = it in read, write = it in write) }

/** The admin-only zones this profile holds (either side): shown as a read-only pill, never a switch. */
fun terminalOnlyZones(profile: MemoryProfile): List<String> =
    ADMIN_ONLY_ZONES.filter { it in profile.read || it in profile.write }

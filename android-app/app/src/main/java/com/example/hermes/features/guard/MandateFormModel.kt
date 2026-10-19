package com.example.hermes.features.guard

import com.example.hermes.core.model.MemoryProfile
import com.example.hermes.features.profiles.MandateDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a host entry covers, in words: "*.site.org" is its subdomains and not site.org itself. */
fun hostScope(host: String): String =
    if (host.startsWith("*.")) "sous-domaines, pas ${host.removePrefix("*.")}" else "ce site seul"

private val EXPIRY = DateTimeFormatter.ofPattern("d MMM 'à' HH:mm", Locale.FRENCH)

/** The end of a mandate started now, in the phone's time zone ("19 oct. à 22:00"). */
internal fun expiryLabel(nowMillis: Long, duration: MandateDuration, zone: ZoneId = ZoneId.systemDefault()): String =
    EXPIRY.format(Instant.ofEpochMilli(nowMillis + duration.hours * 3_600_000L).atZone(zone))

/** A mandate goes to the active profile, and only if it has an active memory profile of the same name. */
fun mandateProfileFor(activeProfile: String, memoryProfiles: List<MemoryProfile>): String? =
    memoryProfiles.firstOrNull { it.name == activeProfile && it.state == "active" }?.name

sealed interface MandateBlock {
    data object FixSite : MandateBlock
    data class Issue(val message: String) : MandateBlock
}

/** Why « Créer le mandat » is off: a site typed but invalid comes first, then the form's own issue. */
fun mandateBlock(hostDraftError: String?, issueMessage: String?): MandateBlock? = when {
    hostDraftError != null -> MandateBlock.FixSite
    issueMessage != null -> MandateBlock.Issue(issueMessage)
    else -> null
}

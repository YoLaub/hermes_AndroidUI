package com.example.hermes.features.profiles

import com.example.hermes.core.model.MemoryMandate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The server's limits, mirrored so the form prevents the 422s. The server stays the judge. */
internal const val MANDATE_MAX_ITEMS = 50
internal const val MANDATE_LABEL_MAX = 100

private val HOST_NAME = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
private val INTERNAL_SUFFIXES = listOf(".internal", ".local", ".localhost", ".lan", ".home", ".corp")

internal data class HostParse(val host: String?, val error: String? = null)

/**
 * One host entry. "site.org" is that host only; with [subdomains] it becomes "*.site.org", which covers its
 * subdomains but not site.org itself (the two are separate entries). No scheme, path, port, address or private name.
 */
internal fun parseMandateHost(input: String, subdomains: Boolean): HostParse {
    val typed = input.trim().lowercase(Locale.ROOT)
    val bare = typed.removePrefix("*.")
    val reason = when {
        bare.isEmpty() -> "Saisis un nom de site"
        "://" in bare || '/' in bare || ':' in bare || '?' in bare || '#' in bare || '@' in bare ->
            "Un nom de site seul : sans http://, chemin ni port"
        !HOST_NAME.matches(bare) -> "Ce n'est pas un nom de site public (ex. site.org)"
        bare.last().isDigit() && bare.split('.').all { part -> part.all { it.isDigit() } } -> "Une adresse IP n'est pas acceptée"
        !bare.substringAfterLast('.').any { it.isLetter() } -> "Une adresse IP n'est pas acceptée"
        INTERNAL_SUFFIXES.any { bare.endsWith(it) } -> "Les noms internes (.local, .lan, .internal…) ne sont pas acceptés"
        else -> null
    }
    if (reason != null) return HostParse(null, reason)
    return HostParse(if (subdomains || typed.startsWith("*.")) "*.$bare" else bare)
}

internal data class FolderParse(val folder: String?, val error: String? = null)

/** One absolute folder, tidied (no double or trailing slash). Sensitive places are left to the server's 422. */
internal fun parseMandateFolder(input: String): FolderParse {
    val typed = input.trim()
    val segments = typed.split('/').filter { it.isNotEmpty() }
    val reason = when {
        !typed.startsWith("/") -> "Un chemin absolu, qui commence par /"
        segments.isEmpty() -> "Pas la racine : choisis un dossier précis"
        segments.any { it == ".." || it == "." } -> "Pas de « .. » ni de « . » dans le chemin"
        segments.any { it.startsWith("~") } -> "Pas de raccourci ~ : écris le chemin complet"
        else -> null
    }
    if (reason != null) return FolderParse(null, reason)
    return FolderParse("/" + segments.joinToString("/"))
}

internal data class MandateDuration(val label: String, val hours: Long)

internal val MANDATE_DURATIONS = listOf(
    MandateDuration("1 h", 1),
    MandateDuration("8 h", 8),
    MandateDuration("24 h", 24),
    MandateDuration("7 jours", 24 * 7),
    MandateDuration("30 jours", 24 * 30)
)

private fun utcFormat(pattern: String) = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }

/** The end as UTC with its offset ("2026-10-20T10:00:00+00:00"): a time without offset is refused by the server. */
internal fun mandateExpiresAt(nowMillis: Long, duration: MandateDuration): String =
    utcFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'").format(Date(nowMillis + duration.hours * 3_600_000L))

internal enum class MandateFormIssue(val message: String) {
    NO_PROFILE("Choisis un profil"),
    LABEL("Le libellé doit faire de 1 à 100 caractères"),
    NOTHING_ENTRUSTED("Ajoute au moins un site ou un dossier"),
    TOO_MANY("50 sites et 50 dossiers au plus")
}

internal fun mandateFormIssue(profile: String?, label: String, hosts: List<String>, folders: List<String>): MandateFormIssue? = when {
    profile.isNullOrBlank() -> MandateFormIssue.NO_PROFILE
    label.trim().length !in 1..MANDATE_LABEL_MAX -> MandateFormIssue.LABEL
    hosts.isEmpty() && folders.isEmpty() -> MandateFormIssue.NOTHING_ENTRUSTED
    hosts.size > MANDATE_MAX_ITEMS || folders.size > MANDATE_MAX_ITEMS -> MandateFormIssue.TOO_MANY
    else -> null
}

internal data class GroupedMandates(val active: List<MemoryMandate>, val ended: List<MemoryMandate>)

/** Active ones first, the soonest to end on top; ended (expired or revoked) after, in the order the server gave. */
internal fun groupMandates(items: List<MemoryMandate>): GroupedMandates {
    val (active, ended) = items.partition { it.state == "active" }
    return GroupedMandates(active.sortedBy { it.expiresAt.orEmpty() }, ended.sortedBy { it.expiresAt.orEmpty() })
}

private val FRACTION = Regex("\\.\\d+(?=[+-]\\d\\d:\\d\\d$|Z$)")

private fun parseInstant(text: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).parse(FRACTION.replace(text, ""))?.time
}.getOrNull()

/** "1 j 2 h", "2 h 5 min", "30 min", "moins d'une minute", or "terminé"; empty when the date cannot be read. */
internal fun mandateTimeLeft(expiresAt: String?, nowMillis: Long): String {
    val end = expiresAt?.let(::parseInstant) ?: return ""
    val minutes = (end - nowMillis) / 60_000L
    return when {
        end <= nowMillis -> "terminé"
        minutes < 1 -> "moins d'une minute"
        minutes >= 24 * 60 -> "${minutes / (24 * 60)} j ${(minutes % (24 * 60)) / 60} h"
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "$minutes min"
    }
}

/** The server's own wording when it refuses (shown as is), a plain sentence for an unknown profile, else the usual. */
internal fun mandateErrorMessage(error: Throwable): String =
    if (error.message.orEmpty().startsWith("HTTP 404")) "Ce profil n'existe plus ou est révoqué"
    else profileErrorMessage(error)

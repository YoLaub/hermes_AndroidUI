package com.example.hermes.core.mobilecontrol

import java.text.Normalizer

data class ContactEntry(val name: String, val number: String)

/**
 * Who a send or a call may target: only a contact of the user, found by exact name or by number, never guessed.
 * What is dialled or texted is always the contact's own stored number, not what the agent typed.
 */
object RecipientPure {
    const val MAX_TEXT_CHARS = 300

    sealed interface Resolution {
        data class Found(val contact: ContactEntry) : Resolution
        data object NotFound : Resolution
        data object Ambiguous : Resolution
    }

    fun resolve(query: String, contacts: List<ContactEntry>): Resolution {
        val lines = collapse(matchesFor(query, contacts))
        return when {
            lines.isEmpty() -> Resolution.NotFound
            lines.size > 1 -> Resolution.Ambiguous
            else -> Resolution.Found(lines.first())
        }
    }

    /** How many different phone lines answer to this query (counts only, for diagnostics). */
    fun distinctLines(query: String, contacts: List<ContactEntry>): Int = collapse(matchesFor(query, contacts)).size

    private fun matchesFor(query: String, contacts: List<ContactEntry>): List<ContactEntry> {
        val q = normalise(query)
        if (q.isEmpty()) return emptyList()
        if (q.any { it.isLetter() }) return contacts.filter { normalise(it.name).equals(q, ignoreCase = true) }
        val digits = digitsOf(q)
        if (digits.isEmpty()) return emptyList()
        return contacts.filter { sameNumber(digits, digitsOf(it.number)) }
    }

    /**
     * Android lists a person once per account (Google, WhatsApp, SIM) and per saved number, often the same line written
     * as "06 12 34 56 78" and "+33 6 12 34 56 78". Copies of one line under one name are ONE recipient; the international
     * form is kept. Only genuinely different lines stay ambiguous.
     */
    private fun collapse(matches: List<ContactEntry>): List<ContactEntry> {
        val out = ArrayList<ContactEntry>()
        for (m in matches) {
            val i = out.indexOfFirst {
                normalise(it.name).equals(normalise(m.name), ignoreCase = true) &&
                    sameNumber(digitsOf(it.number), digitsOf(m.number))
            }
            when {
                i < 0 -> out += m
                m.number.trim().startsWith("+") && !out[i].number.trim().startsWith("+") -> out[i] = m
            }
        }
        return out
    }

    // Accented names compare equal whether the accent is one character or a letter plus a combining mark.
    private fun normalise(s: String) = Normalizer.normalize(s.trim(), Normalizer.Form.NFC)

    /** Null when the text may be sent, otherwise why not. */
    fun textProblem(text: String?): String? = when {
        text == null || text.isBlank() -> "Le texte du SMS est vide."
        text.length > MAX_TEXT_CHARS -> "Le texte dépasse $MAX_TEXT_CHARS caractères : il doit tenir en entier sur l'écran de confirmation."
        else -> null
    }

    private fun digitsOf(s: String) = s.filter { it.isDigit() }

    // Same line whatever the prefix: "+33 6 12..." and "06 12...". Short strings (3615, 112) never match by suffix.
    private fun sameNumber(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        return a.length >= 9 && b.length >= 9 && a.takeLast(9) == b.takeLast(9)
    }
}

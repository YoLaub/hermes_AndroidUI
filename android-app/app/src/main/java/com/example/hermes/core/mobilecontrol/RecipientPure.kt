package com.example.hermes.core.mobilecontrol

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
        val q = query.trim()
        if (q.isEmpty()) return Resolution.NotFound
        val matches = if (q.any { it.isLetter() }) {
            contacts.filter { it.name.trim().equals(q, ignoreCase = true) }
        } else {
            val digits = digitsOf(q)
            if (digits.isEmpty()) return Resolution.NotFound
            contacts.filter { sameNumber(digits, digitsOf(it.number)) }
        }
        val distinct = matches.map { it.name.trim().lowercase() to digitsOf(it.number) }.distinct()
        return when {
            matches.isEmpty() -> Resolution.NotFound
            distinct.size > 1 -> Resolution.Ambiguous
            else -> Resolution.Found(matches.first())
        }
    }

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

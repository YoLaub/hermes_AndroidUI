package com.example.hermes.core.mobilecontrol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An SMS as read from Android, before filtering. */
data class RawSms(
    val address: String?,
    val contactName: String?,
    val dateMs: Long,
    val body: String?,
    val incoming: Boolean
)

/** A call-log row as read from Android, before filtering. */
data class RawCall(
    val number: String?,
    val contactName: String?,
    val dateMs: Long,
    val durationSec: Long,
    val androidType: Int
)

/** One SMS, reduced to what the user consented to share. One-time codes are already masked. Never stored or logged. */
@Serializable
data class MobileSms(
    val sender: String,
    @SerialName("date_ms") val dateMs: Long,
    val text: String? = null,
    val incoming: Boolean = true
)

@Serializable
data class MobileCallEntry(
    val who: String,
    val direction: String,
    @SerialName("date_ms") val dateMs: Long,
    @SerialName("duration_sec") val durationSec: Long
)

/**
 * Pure rules for reading messages and the call log: the last 24 hours, at most 20 entries, newest first,
 * contact name when known, one-time codes replaced by [code] BEFORE anything leaves the phone.
 */
object MessagePure {
    const val WINDOW_MS = 24L * 60 * 60 * 1000
    const val MAX_MESSAGES = 20
    const val MAX_TEXT_CHARS = 300
    private const val CODE_PLACEHOLDER = "[code]"

    // A run of digits, possibly split by single spaces or dashes ("123 456", "123-456").
    private val DIGIT_RUN = Regex("""\d(?:[ -]?\d)*""")

    /** Runs of 4 to 8 digits look like one-time codes and PINs. Shorter numbers and phone numbers (10+) are kept. */
    fun maskCodes(text: String): String = DIGIT_RUN.replace(text) { m ->
        val digits = m.value.count { it.isDigit() }
        if (digits in 4..8) CODE_PLACEHOLDER else m.value
    }

    fun toMessages(rows: List<RawSms>, nowMs: Long): List<MobileSms> =
        rows.filter { it.dateMs in (nowMs - WINDOW_MS)..nowMs }
            .sortedByDescending { it.dateMs }
            .take(MAX_MESSAGES)
            .map {
                MobileSms(
                    sender = it.contactName?.trim()?.takeIf { n -> n.isNotEmpty() } ?: (it.address ?: "?"),
                    dateMs = it.dateMs,
                    text = it.body?.trim()?.takeIf { b -> b.isNotEmpty() }?.let { b -> maskCodes(b).take(MAX_TEXT_CHARS) },
                    incoming = it.incoming
                )
            }

    fun toCalls(rows: List<RawCall>, nowMs: Long): List<MobileCallEntry> =
        rows.filter { it.dateMs in (nowMs - WINDOW_MS)..nowMs }
            .sortedByDescending { it.dateMs }
            .take(MAX_MESSAGES)
            .map {
                MobileCallEntry(
                    who = it.contactName?.trim()?.takeIf { n -> n.isNotEmpty() } ?: (it.number ?: "?"),
                    direction = when (it.androidType) {
                        1 -> "incoming"
                        2 -> "outgoing"
                        3 -> "missed"
                        5 -> "rejected"
                        else -> "other"
                    },
                    dateMs = it.dateMs,
                    durationSec = maxOf(0L, it.durationSec)
                )
            }
}

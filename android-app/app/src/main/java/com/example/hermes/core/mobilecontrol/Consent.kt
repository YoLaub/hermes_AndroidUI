package com.example.hermes.core.mobilecontrol

/**
 * The per-session consents the user can give, by name. One list on the wire (`allow`) instead of one field per
 * consent. Every consent is off unless the user switched it on for THIS session, and the effective set is the
 * intersection of the phone's choice and the relay's echo: the relay can remove a consent, never grant one.
 */
object Consent {
    const val SCREENSHOTS = "screenshots"
    const val CALENDAR = "calendar"
    const val SMS_READ = "sms_read"
    const val CALL_LOG_READ = "call_log_read"
    const val SMS_SEND = "sms_send"
    const val CALL_PLACE = "call_place"

    val KNOWN: Set<String> = setOf(SCREENSHOTS, CALENDAR, SMS_READ, CALL_LOG_READ, SMS_SEND, CALL_PLACE)

    /** Android runtime permissions a consent needs before it can be given (none: the accessibility service). */
    val PERMISSIONS: Map<String, List<String>> = mapOf(
        CALENDAR to listOf("android.permission.READ_CALENDAR"),
        SMS_READ to listOf("android.permission.READ_SMS"),
        CALL_LOG_READ to listOf("android.permission.READ_CALL_LOG"),
        // Contacts are required here: a recipient must be one of the user's contacts.
        SMS_SEND to listOf("android.permission.SEND_SMS", "android.permission.READ_CONTACTS"),
        CALL_PLACE to listOf("android.permission.CALL_PHONE", "android.permission.READ_CONTACTS")
    )

    private val BY_OPERATION = mapOf(
        "screenshot" to SCREENSHOTS,
        "tap_xy" to SCREENSHOTS,
        "calendar_read" to CALENDAR,
        "sms_read" to SMS_READ,
        "call_log_read" to CALL_LOG_READ,
        "sms_send" to SMS_SEND,
        "call_place" to CALL_PLACE
    )

    fun requiredFor(operation: String): String? = BY_OPERATION[operation]

    fun refusalCode(consent: String): String = when (consent) {
        SCREENSHOTS -> "SCREENSHOTS_NOT_ALLOWED"
        CALENDAR -> "CALENDAR_NOT_ALLOWED"
        SMS_READ -> "SMS_NOT_ALLOWED"
        CALL_LOG_READ -> "CALL_LOG_NOT_ALLOWED"
        SMS_SEND -> "SMS_SEND_NOT_ALLOWED"
        CALL_PLACE -> "CALL_NOT_ALLOWED"
        else -> "CONSENT_NOT_GIVEN"
    }

    fun refusalMessage(consent: String): String = when (consent) {
        SCREENSHOTS -> "L'utilisateur n'a pas autorisé les captures d'écran pour cette session sur le téléphone."
        CALENDAR -> "L'utilisateur n'a pas autorisé la lecture du calendrier pour cette session sur le téléphone."
        SMS_READ -> "L'utilisateur n'a pas autorisé la lecture des SMS pour cette session sur le téléphone."
        CALL_LOG_READ -> "L'utilisateur n'a pas autorisé la lecture du journal d'appels pour cette session sur le téléphone."
        SMS_SEND -> "L'utilisateur n'a pas autorisé l'envoi de SMS pour cette session sur le téléphone."
        CALL_PLACE -> "L'utilisateur n'a pas autorisé les appels pour cette session sur le téléphone."
        else -> "L'utilisateur n'a pas donné ce consentement pour cette session sur le téléphone."
    }

    /**
     * What the relay says it holds. The `allow` list wins; an older relay only knows the two legacy booleans.
     * Unknown names are dropped.
     */
    fun echoed(list: List<String>?, screenshotsLegacy: Boolean?, calendarLegacy: Boolean?): Set<String> {
        if (list != null) return list.filter { it in KNOWN }.toSet()
        return buildSet {
            if (screenshotsLegacy == true) add(SCREENSHOTS)
            if (calendarLegacy == true) add(CALENDAR)
        }
    }
}

package com.example.hermes.core.mobilecontrol

/** How sensitive a per-session consent is, for grouping them on screen. It changes nothing in what they allow. */
enum class ConsentLevel { Read, ConfirmedWrite, CallsAndSms }

fun consentLevel(consent: String): ConsentLevel? = when (consent) {
    Consent.SCREENSHOTS, Consent.CALENDAR, Consent.SMS_READ, Consent.CALL_LOG_READ -> ConsentLevel.Read
    Consent.CALENDAR_WRITE -> ConsentLevel.ConfirmedWrite
    Consent.SMS_SEND, Consent.CALL_PLACE -> ConsentLevel.CallsAndSms
    else -> null
}

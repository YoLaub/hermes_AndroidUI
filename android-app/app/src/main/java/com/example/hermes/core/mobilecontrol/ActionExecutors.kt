package com.example.hermes.core.mobilecontrol

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SmsManager

/**
 * The device side of a confirmed send or call. None of this runs without the user's tap on the confirmation
 * (see ActionConfirmer). Not unit-testable on the JVM: needs a device check.
 */
class ContactsReader(private val context: Context) {
    /** Every contact with a phone number: the only recipients an action may target. */
    fun all(): List<ContactEntry> {
        val out = ArrayList<ContactEntry>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext() && out.size < 5000) {
                val name = c.getString(0)
                val number = c.getString(1)
                if (!name.isNullOrBlank() && !number.isNullOrBlank()) out += ContactEntry(name, number)
            }
        }
        return out
    }
}

class SmsSender(private val context: Context) {
    /** Hands the text to the phone's messaging service. "Sent" here does not mean delivered. */
    fun send(number: String, text: String) {
        @Suppress("DEPRECATION")
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
        val parts = sms.divideMessage(text)
        if (parts.size > 1) sms.sendMultipartTextMessage(number, null, parts, null, null)
        else sms.sendTextMessage(number, null, text, null, null)
    }
}

class CallPlacer(private val context: Context) {
    fun place(number: String) {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
    }
}

package com.example.hermes.core.mobilecontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Contact names for phone numbers. Optional: without READ_CONTACTS the number is shown instead. */
private class ContactNames(private val context: Context) {
    private val cache = HashMap<String, String?>()

    fun nameFor(number: String?): String? {
        if (number.isNullOrBlank() || !context.granted(Manifest.permission.READ_CONTACTS)) return null
        return cache.getOrPut(number) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }
    }
}

/**
 * Reads recent SMS (inbox and sent only) newest first, at most [MessagePure.MAX_MESSAGES]. Not unit-testable on the
 * JVM: needs a device check. The text is masked and cut by [MessagePure] before it leaves the phone.
 */
class SmsReader(private val context: Context) {
    fun hasPermission() = context.granted(Manifest.permission.READ_SMS)

    fun read(nowMs: Long): List<RawSms> {
        val names = ContactNames(context)
        val rows = ArrayList<RawSms>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY, Telephony.Sms.TYPE),
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.TYPE} IN (?, ?)",
            arrayOf((nowMs - MessagePure.WINDOW_MS).toString(),
                Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), Telephony.Sms.MESSAGE_TYPE_SENT.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && rows.size < MessagePure.MAX_MESSAGES) {
                val address = c.getString(0)
                rows += RawSms(
                    address = address,
                    contactName = names.nameFor(address),
                    dateMs = c.getLong(1),
                    body = c.getString(2),
                    incoming = c.getInt(3) == Telephony.Sms.MESSAGE_TYPE_INBOX
                )
            }
        }
        return rows
    }
}

/** Reads the recent call log, newest first, at most [MessagePure.MAX_MESSAGES]. Needs a device check. */
class CallLogReader(private val context: Context) {
    fun hasPermission() = context.granted(Manifest.permission.READ_CALL_LOG)

    fun read(nowMs: Long): List<RawCall> {
        val rows = ArrayList<RawCall>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE,
                CallLog.Calls.DURATION, CallLog.Calls.TYPE),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf((nowMs - MessagePure.WINDOW_MS).toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && rows.size < MessagePure.MAX_MESSAGES) {
                rows += RawCall(
                    number = c.getString(0),
                    contactName = c.getString(1),
                    dateMs = c.getLong(2),
                    durationSec = c.getLong(3),
                    androidType = c.getInt(4)
                )
            }
        }
        return rows
    }
}

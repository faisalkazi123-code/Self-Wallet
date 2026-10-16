package com.selfwallet.app.sms

import android.content.Context
import android.provider.Telephony
import com.selfwallet.app.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads SMS already in the inbox so a new user can back-fill past spending. */
object SmsImporter {
    data class Outcome(val scanned: Int, val added: Int)

    suspend fun importInbox(context: Context, repo: Repository, sinceMillis: Long): Outcome = withContext(Dispatchers.IO) {
        var scanned = 0
        var added = 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )
        cursor?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                scanned++
                val sender = c.getString(iAddr) ?: continue
                val body = c.getString(iBody) ?: continue
                val date = c.getLong(iDate)
                if (repo.ingestSms(sender, body, date, notifyChange = false) != null) added++
            }
        }
        if (added > 0) repo.notifyChanged()
        Outcome(scanned, added)
    }
}

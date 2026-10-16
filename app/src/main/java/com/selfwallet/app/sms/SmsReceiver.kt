package com.selfwallet.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.selfwallet.app.SelfWalletApp
import kotlinx.coroutines.launch

/** Listens for incoming SMS and adds bank / card messages as transactions automatically. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        if (messages.isEmpty()) return

        // Multi-part SMS arrive as several PDUs from the same sender: stitch them back together.
        val grouped = messages.filterNotNull().groupBy { it.originatingAddress.orEmpty() }
        val app = context.applicationContext as SelfWalletApp
        val pending = goAsync()
        app.container.scope.launch {
            try {
                val settings = app.container.settings.current()
                if (!settings.smsAutoImport) return@launch
                grouped.forEach { (sender, parts) ->
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val time = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                    val tx = app.container.repository.ingestSms(sender, body, time)
                    if (tx != null && settings.smsNotify) {
                        Notifications.showAdded(context, tx, settings.hideAmounts)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

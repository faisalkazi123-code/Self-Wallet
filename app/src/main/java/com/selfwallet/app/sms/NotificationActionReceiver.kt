package com.selfwallet.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.selfwallet.app.SelfWalletApp
import kotlinx.coroutines.launch

/** Handles the "Personal" / "Corporate" buttons on an auto-added transaction notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val txId = intent.getLongExtra(Notifications.EXTRA_TX_ID, 0L)
        val kind = intent.getStringExtra(Notifications.EXTRA_KIND) ?: return
        if (txId == 0L) return
        val app = context.applicationContext as SelfWalletApp
        val pending = goAsync()
        app.container.scope.launch {
            try {
                app.container.repository.setSpendKind(txId, kind)
                NotificationManagerCompat.from(context).cancel(txId.toInt())
            } finally {
                pending.finish()
            }
        }
    }
}

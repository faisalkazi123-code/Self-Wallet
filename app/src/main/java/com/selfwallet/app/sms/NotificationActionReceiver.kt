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
        if (txId == 0L) return
        val kind = intent.getStringExtra(Notifications.EXTRA_KIND)
        val resolveDup = intent.action == Notifications.ACTION_RESOLVE_DUP
        if (kind == null && !resolveDup) return
        val app = context.applicationContext as SelfWalletApp
        val pending = goAsync()
        app.container.scope.launch {
            try {
                if (resolveDup) {
                    app.container.repository.resolveDuplicate(txId, keep = intent.getBooleanExtra(Notifications.EXTRA_KEEP, true))
                } else if (kind != null) {
                    app.container.repository.setSpendKind(txId, kind)
                }
                NotificationManagerCompat.from(context).cancel(txId.toInt())
            } finally {
                pending.finish()
            }
        }
    }
}

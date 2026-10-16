package com.selfwallet.app.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.selfwallet.app.MainActivity
import com.selfwallet.app.R
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TransactionEntity
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.util.Currencies

object Notifications {
    private const val CHANNEL = "auto_added"
    const val EXTRA_TX_ID = "tx_id"
    const val EXTRA_KIND = "kind"
    private const val ACTION_SET_KIND = "com.selfwallet.app.SET_KIND"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL, "Auto-added transactions", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Shown when a bank SMS is turned into a transaction"
        }
        nm.createNotificationChannel(ch)
    }

    fun showAdded(context: Context, tx: TransactionEntity, hideAmount: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TX_ID, tx.id)
        }
        val pi = PendingIntent.getActivity(
            context, tx.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val amount = if (hideAmount) "••••" else Currencies.format(tx.amount, tx.currency)
        val verb = if (tx.type == TxType.INCOME) "Received" else "Spent"
        val title = "$verb $amount" + if (tx.merchant.isNotBlank()) " · ${tx.merchant}" else ""
        val text = if (tx.type == TxType.EXPENSE) "Personal or corporate? Tap a button below, or open to edit." else "Added automatically from SMS."

        fun kindAction(kind: String, label: String, requestOffset: Int): NotificationCompat.Action {
            val i = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_SET_KIND
                putExtra(EXTRA_TX_ID, tx.id)
                putExtra(EXTRA_KIND, kind)
            }
            val p = PendingIntent.getBroadcast(
                context, tx.id.toInt() * 4 + requestOffset, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action.Builder(0, label, p).build()
        }

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(0xFF0F766E.toInt())
        if (tx.type == TxType.EXPENSE) {
            builder.addAction(kindAction(SpendKind.PERSONAL, "Personal", 1))
            builder.addAction(kindAction(SpendKind.CORPORATE, "Corporate", 2))
        }
        runCatching { NotificationManagerCompat.from(context).notify(tx.id.toInt(), builder.build()) }
    }
}

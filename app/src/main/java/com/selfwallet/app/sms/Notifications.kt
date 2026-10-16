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
import com.selfwallet.app.data.DuplicateCheck
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TransactionEntity
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Dates

object Notifications {
    private const val CHANNEL = "auto_added"
    const val EXTRA_TX_ID = "tx_id"
    const val EXTRA_KIND = "kind"
    const val EXTRA_KEEP = "keep"
    const val ACTION_SET_KIND = "com.selfwallet.app.SET_KIND"
    const val ACTION_RESOLVE_DUP = "com.selfwallet.app.RESOLVE_DUP"
    private const val DUP_CHANNEL = "duplicates"
    private const val BUDGET_CHANNEL = "budgets"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL, "Auto-added transactions", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Shown when a bank SMS is turned into a transaction"
        }
        nm.createNotificationChannel(ch)
        val dup = NotificationChannel(DUP_CHANNEL, "Possible duplicate payments", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Warns when the same payment SMS arrives twice or you may have been charged twice"
        }
        nm.createNotificationChannel(dup)
        nm.createNotificationChannel(
            NotificationChannel(BUDGET_CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a monthly budget reaches 80% or is exceeded"
            },
        )
    }

    fun showBudget(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            context, 7, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, BUDGET_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(0xFFB45309.toInt())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(title.hashCode(), n) }
    }

    /** [original] is the earlier transaction when [tx] is a possible duplicate. */
    fun showAdded(context: Context, tx: TransactionEntity, hideAmount: Boolean, original: TransactionEntity? = null) {
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

        fun button(label: String, requestOffset: Int, fill: Intent.() -> Unit): NotificationCompat.Action {
            val i = Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra(EXTRA_TX_ID, tx.id)
                fill()
            }
            val p = PendingIntent.getBroadcast(
                context, tx.id.toInt() * 4 + requestOffset, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action.Builder(0, label, p).build()
        }

        fun kindAction(kind: String, label: String, requestOffset: Int) = button(label, requestOffset) {
            action = ACTION_SET_KIND
            putExtra(EXTRA_KIND, kind)
        }

        if (tx.dupStatus == DuplicateCheck.PENDING) {
            val sameSms = tx.dupKind == DuplicateCheck.SAME_SMS
            val earlier = original?.let { "${if (hideAmount) "••••" else Currencies.format(it.amount, it.currency)} at ${Dates.time(it.timestamp)}" }
            val dupTitle = if (sameSms) "⚠ Same payment SMS received again" else "⚠ Possible double charge"
            val dupText = buildString {
                append("$amount")
                if (tx.merchant.isNotBlank()) append(" · ${tx.merchant}")
                if (earlier != null) append(" — already recorded $earlier.")
                append(
                    if (sameSms) " Not counted yet. Check your bank app: were you charged twice, or is it just a repeat SMS?"
                    else " Check your bank app or statement. If you were charged twice, contact your bank to dispute it.",
                )
            }
            fun resolve(label: String, keep: Boolean, offset: Int) = button(label, offset) {
                action = ACTION_RESOLVE_DUP
                putExtra(EXTRA_KEEP, keep)
            }
            val n = NotificationCompat.Builder(context, DUP_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_wallet)
                .setContentTitle(dupTitle)
                .setContentText(dupText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(dupText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setColor(0xFFE11D48.toInt())
                .addAction(
                    if (sameSms) resolve("Just a repeat SMS · remove", keep = false, offset = 1)
                    else resolve("Remove this one", keep = false, offset = 1),
                )
                .addAction(
                    if (sameSms) resolve("Charged twice · keep", keep = true, offset = 2)
                    else resolve("Both are real", keep = true, offset = 2),
                )
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(tx.id.toInt(), n) }
            return
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

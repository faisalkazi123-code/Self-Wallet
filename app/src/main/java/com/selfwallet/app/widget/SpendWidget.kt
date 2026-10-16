package com.selfwallet.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.selfwallet.app.MainActivity
import com.selfwallet.app.R
import com.selfwallet.app.SelfWalletApp
import com.selfwallet.app.data.DuplicateCheck
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.sms.Notifications
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Dates
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Home-screen widget: today's and this month's spending, plus a quick "+ Add" button. */
class SpendWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as SelfWalletApp
        val pending = goAsync()
        app.container.scope.launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Re-draws every placed widget. Cheap; called after data changes and daily. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SpendWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, SpendWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = (context.applicationContext as SelfWalletApp).container.repository
            val s = repo.settings.current()
            val rates = repo.allRates().associate { it.currency to it.rate }
            val txs = repo.allTransactions().filter { it.type == TxType.EXPENSE && !DuplicateCheck.isUncounted(it) }
            fun base(a: Double, c: String) = a * (if (c == s.baseCurrency) 1.0 else rates[c] ?: 1.0)
            val todayStart = Dates.startOf(LocalDate.now())
            val today = txs.filter { it.timestamp >= todayStart }.sumOf { base(it.amount, it.currency) }
            val month = txs.filter { it.timestamp in Dates.monthRange(YearMonth.now()) }.sumOf { base(it.amount, it.currency) }
            fun show(v: Double) = if (s.hideAmounts) "••••" else Currencies.format(v, s.baseCurrency)

            val open = PendingIntent.getActivity(
                context, 100, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val add = PendingIntent.getActivity(
                context, 101,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(Notifications.EXTRA_OPEN, Notifications.OPEN_ADD),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_spend).apply {
                    setTextViewText(R.id.widget_today, show(today))
                    setTextViewText(R.id.widget_month, "This month ${show(month)}")
                    setOnClickPendingIntent(R.id.widget_root, open)
                    setOnClickPendingIntent(R.id.widget_add, add)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}

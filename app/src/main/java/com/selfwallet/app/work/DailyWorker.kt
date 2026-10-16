package com.selfwallet.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.selfwallet.app.SelfWalletApp
import com.selfwallet.app.data.DuplicateCheck
import com.selfwallet.app.data.Insights
import com.selfwallet.app.data.db.Claim
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.data.db.TxType
import com.selfwallet.app.sms.Notifications
import com.selfwallet.app.util.Currencies
import com.selfwallet.app.util.Dates
import com.selfwallet.app.widget.SpendWidget
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Runs once a day (and when the app starts): bill and subscription reminders, the monthly
 * summary on the 1st, and a widget refresh. Everything is computed on the phone.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SelfWalletApp
        val repo = app.container.repository
        val settings = repo.settings.current()
        val today = LocalDate.now()

        if (settings.reminders) {
            // ---- bills from reminder SMS ----
            repo.getBills().filter { !it.paid }.forEach { bill ->
                val days = bill.dueDay - today.toEpochDay()
                val level = when {
                    days == 0L -> 2
                    days in 1..2 -> 1
                    else -> 0
                }
                if (level > bill.reminded) {
                    Notifications.showBill(applicationContext, bill, settings.hideAmounts, saved = false)
                    repo.saveBill(bill.copy(reminded = level))
                }
            }

            // ---- recurring payments (subscriptions, rent...) ----
            val payments = repo.allTransactions()
                .filter { it.type == TxType.EXPENSE && !DuplicateCheck.isUncounted(it) }
                .map { Insights.Payment(it.merchant, it.amount, it.currency, Dates.toDate(it.timestamp)) }
            val reminded = settings.remindedRecurring.toMutableSet()
            Insights.recurring(payments).forEach { r ->
                val days = r.next.toEpochDay() - today.toEpochDay()
                val key = "${r.merchant.lowercase()}|${r.next}"
                if (days in 0..2 && key !in reminded) {
                    val amount = if (settings.hideAmounts) "••••" else Currencies.format(r.amount, r.currency)
                    val whenText = when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $days days" }
                    Notifications.showReminder(
                        applicationContext, key,
                        "${r.merchant} renews $whenText",
                        "${r.label} payment of about $amount. Cancel it before then if you no longer use it.",
                    )
                    reminded += key
                }
            }
            // Keep the set small: forget reminders for past dates.
            repo.settings.setRemindedRecurring(
                reminded.filter { k -> runCatching { LocalDate.parse(k.substringAfterLast('|')) >= today.minusDays(7) }.getOrDefault(false) }.toSet(),
            )
        }

        // ---- monthly summary for last month ----
        val lastMonth = YearMonth.from(today).minusMonths(1)
        if (today.dayOfMonth <= 3 && settings.lastSummaryMonth != lastMonth.toString()) {
            sendSummary(app, lastMonth)
            repo.settings.setLastSummaryMonth(lastMonth.toString())
        }

        SpendWidget.refresh(applicationContext)
        return Result.success()
    }

    private suspend fun sendSummary(app: SelfWalletApp, month: YearMonth) {
        val repo = app.container.repository
        val s = repo.settings.current()
        val rates = repo.allRates().associate { it.currency to it.rate }
        val categories = repo.allCategories().associateBy { it.id }
        val allocations = repo.allAllocations().associateBy { it.id }
        val txs = repo.allTransactions().filter { it.type == TxType.EXPENSE && !DuplicateCheck.isUncounted(it) }
        fun base(amount: Double, cur: String) = amount * (if (cur == s.baseCurrency) 1.0 else rates[cur] ?: 1.0)
        val thisRange = Dates.monthRange(month)
        val prevRange = Dates.monthRange(month.minusMonths(1))
        val current = txs.filter { it.timestamp in thisRange }
        if (current.isEmpty()) return
        val total = current.sumOf { base(it.amount, it.currency) }
        val previous = txs.filter { it.timestamp in prevRange }.sumOf { base(it.amount, it.currency) }
        val top = current.groupBy { it.categoryId }.maxByOrNull { (_, l) -> l.sumOf { base(it.amount, it.currency) } }
        val topName = top?.key?.let { categories[it]?.name } ?: "Uncategorised"
        val toClaim = txs.filter { t ->
            t.allocationId?.let { allocations[it] }?.kind == SpendKind.CORPORATE && Claim.of(t) != Claim.REIMBURSED
        }.sumOf { base(it.amount, it.currency) }

        val change = if (previous > 0) ((total - previous) / previous * 100).toInt() else null
        val text = buildString {
            append("You spent ${Currencies.format(total, s.baseCurrency)}")
            if (change != null) append(if (change >= 0) " (+$change% vs the month before)" else " ($change% vs the month before)")
            append(". Biggest category: $topName.")
            if (toClaim > 0) append(" ${Currencies.format(toClaim, s.baseCurrency)} of corporate spending still to be reimbursed.")
        }
        Notifications.showInsight(applicationContext, "Your ${Dates.monthLabel(month)} summary", text, Notifications.OPEN_REPORTS)
    }

    companion object {
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                "daily-insights",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build(),
            )
            // Also run shortly after launch so reminders don't wait a full day.
            wm.enqueueUniqueWork("daily-insights-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<DailyWorker>().build())
        }
    }
}

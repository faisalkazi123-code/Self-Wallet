package com.selfwallet.app.data

import java.time.LocalDate
import kotlin.math.abs

/** Pure analysis over past transactions: recurring payments and unusual spending. */
object Insights {

    data class Payment(val merchant: String, val amount: Double, val currency: String, val date: LocalDate)

    data class Recurring(
        val merchant: String,
        val amount: Double,
        val currency: String,
        val everyDays: Int,
        val last: LocalDate,
        val next: LocalDate,
        val count: Int,
    ) {
        val label: String
            get() = when {
                everyDays <= 10 -> "Weekly"
                everyDays <= 40 -> "Monthly"
                everyDays <= 100 -> "Quarterly"
                else -> "Yearly"
            }
    }

    /**
     * Finds merchants charged at a steady rhythm (weekly, monthly, quarterly, yearly) for a
     * similar amount. Needs at least two payments; three for weekly ones.
     */
    fun recurring(payments: List<Payment>): List<Recurring> =
        payments
            .filter { it.merchant.isNotBlank() }
            .groupBy { it.merchant.trim().lowercase() to it.currency }
            .mapNotNull { (_, list) ->
                val sorted = list.sortedBy { it.date }
                if (sorted.size < 2) return@mapNotNull null
                val gaps = sorted.zipWithNext { a, b -> b.date.toEpochDay() - a.date.toEpochDay() }.filter { it > 0 }
                if (gaps.isEmpty()) return@mapNotNull null
                val avg = gaps.average()
                val period = PERIODS.firstOrNull { (lo, hi) -> avg in lo..hi } ?: return@mapNotNull null
                if (period.first < 10 && sorted.size < 3) return@mapNotNull null
                val tolerance = maxOf(3.0, avg * 0.2)
                if (gaps.any { abs(it - avg) > tolerance }) return@mapNotNull null
                val amounts = sorted.map { it.amount }.sorted()
                val median = amounts[amounts.size / 2]
                if (amounts.any { abs(it - median) > median * 0.15 }) return@mapNotNull null
                val last = sorted.last()
                Recurring(
                    merchant = last.merchant,
                    amount = last.amount,
                    currency = last.currency,
                    everyDays = avg.toInt(),
                    last = last.date,
                    next = last.date.plusDays(avg.toLong()),
                    count = sorted.size,
                )
            }
            .sortedBy { it.next }

    private val PERIODS = listOf(6.0 to 8.5, 26.0 to 35.0, 85.0 to 95.0, 355.0 to 375.0)

    /**
     * True when [amount] stands out: at least 3x the median of [history] (same category,
     * base currency) with enough history to judge, or 5x the median of all spending.
     */
    fun isUnusual(amount: Double, categoryHistory: List<Double>, allHistory: List<Double>): Boolean {
        fun median(xs: List<Double>) = xs.sorted().let { it[it.size / 2] }
        if (categoryHistory.size >= 5 && amount >= 3 * median(categoryHistory) && amount - median(categoryHistory) > 0) return true
        if (allHistory.size >= 10 && amount >= 5 * median(allHistory)) return true
        return false
    }
}

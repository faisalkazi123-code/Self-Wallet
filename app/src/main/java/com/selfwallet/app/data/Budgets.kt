package com.selfwallet.app.data

import com.selfwallet.app.data.db.BudgetEntity
import com.selfwallet.app.data.db.SpendKind

/** Pure budget maths, shared by the UI and the alert check. */
object Budgets {
    const val ALL = "ALL"

    /** One expense, already converted to the base currency. */
    data class Line(val amountBase: Double, val categoryId: Long?, val corporate: Boolean)

    fun matches(budget: BudgetEntity, categoryId: Long?, corporate: Boolean): Boolean {
        if (budget.categoryId != null && budget.categoryId != categoryId) return false
        return when (budget.kind) {
            SpendKind.PERSONAL -> !corporate
            SpendKind.CORPORATE -> corporate
            else -> true
        }
    }

    fun spent(budget: BudgetEntity, lines: List<Line>): Double =
        lines.filter { matches(budget, it.categoryId, it.corporate) }.sumOf { it.amountBase }

    /** 0, 80 (close to the limit) or 100 (over it). */
    fun level(spent: Double, limit: Double): Int = when {
        limit <= 0 -> 0
        spent >= limit -> 100
        spent >= limit * 0.8 -> 80
        else -> 0
    }

    fun name(budget: BudgetEntity, categoryName: String?): String {
        val scope = when (budget.kind) {
            SpendKind.PERSONAL -> "Personal"
            SpendKind.CORPORATE -> "Corporate"
            else -> null
        }
        return when {
            categoryName != null && scope != null -> "$categoryName · $scope"
            categoryName != null -> categoryName
            scope != null -> "$scope spending"
            else -> "Total spending"
        }
    }
}

package com.selfwallet.app.data

import com.selfwallet.app.data.db.BudgetEntity
import com.selfwallet.app.data.db.SpendKind
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetsTest {
    private val lines = listOf(
        Budgets.Line(100.0, categoryId = 1, corporate = false),
        Budgets.Line(250.0, categoryId = 2, corporate = true),
        Budgets.Line(50.0, categoryId = 1, corporate = true),
    )

    @Test
    fun totals() {
        assertEquals(400.0, Budgets.spent(BudgetEntity(amount = 500.0), lines), 0.001)
        assertEquals(100.0, Budgets.spent(BudgetEntity(amount = 500.0, kind = SpendKind.PERSONAL), lines), 0.001)
        assertEquals(300.0, Budgets.spent(BudgetEntity(amount = 500.0, kind = SpendKind.CORPORATE), lines), 0.001)
        assertEquals(150.0, Budgets.spent(BudgetEntity(amount = 500.0, categoryId = 1), lines), 0.001)
        assertEquals(50.0, Budgets.spent(BudgetEntity(amount = 500.0, categoryId = 1, kind = SpendKind.CORPORATE), lines), 0.001)
    }

    @Test
    fun levels() {
        assertEquals(0, Budgets.level(79.0, 100.0))
        assertEquals(80, Budgets.level(80.0, 100.0))
        assertEquals(100, Budgets.level(100.0, 100.0))
        assertEquals(0, Budgets.level(10.0, 0.0))
    }
}

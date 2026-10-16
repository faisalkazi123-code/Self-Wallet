package com.selfwallet.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private fun p(m: String, a: Double, d: LocalDate) = Insights.Payment(m, a, "USD", d)

    @Test
    fun findsMonthlySubscription() {
        val base = LocalDate.of(2026, 7, 3)
        val list = listOf(
            p("Netflix", 12.99, base), p("Netflix", 12.99, base.plusDays(31)), p("Netflix", 12.99, base.plusDays(61)),
            p("Carrefour", 40.0, base), p("Carrefour", 120.0, base.plusDays(3)),
        )
        val r = Insights.recurring(list)
        assertEquals(1, r.size)
        assertEquals("Netflix", r[0].merchant)
        assertEquals("Monthly", r[0].label)
        assertEquals(base.plusDays(61 + 30), r[0].next)
    }

    @Test
    fun unusual() {
        val history = listOf(20.0, 25.0, 30.0, 22.0, 28.0)
        assertTrue(Insights.isUnusual(200.0, history, history))
        assertFalse(Insights.isUnusual(40.0, history, history))
    }
}

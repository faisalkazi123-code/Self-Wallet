package com.selfwallet.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SmsParserExtrasTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun readsAvailableBalance() {
        val r = SmsParser.parse("AD-HDFCBK", "Rs.250.00 debited from A/c XX1234 to VPA swiggy@icici. Avl Bal Rs.10,234.50")!!
        assertEquals(10234.5, r.balance!!, 0.001)
        assertEquals("INR", r.balanceCurrency)
        assertEquals(false, r.balanceIsLimit)
    }

    @Test
    fun readsAvailableLimit() {
        val r = SmsParser.parse("ENBD", "AED 45.50 spent on your Credit Card ending 5678 at CARREFOUR DUBAI. Avl limit AED 12,000.00")!!
        assertEquals(12000.0, r.balance!!, 0.001)
        assertTrue(r.balanceIsLimit)
    }

    @Test
    fun cardBillDue() {
        val d = SmsParser.parseDue("AD-HDFCBK", "Your HDFC Bank Credit Card XX4321 bill of Rs 4,500.00 is due on 05-11-2026. Min amt due Rs 450", today)
        assertNotNull(d); d!!
        assertEquals(4500.0, d.amount, 0.001)
        assertEquals(LocalDate.of(2026, 11, 5), d.dueDate)
        assertEquals("4321", d.account)
    }

    @Test
    fun dueWithMonthName() {
        val d = SmsParser.parseDue("ENBD", "Statement for card ending 5678: total amount due AED 2,310.00, payment due by 28 Oct.", today)!!
        assertEquals(2310.0, d.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 28), d.dueDate)
    }

    @Test
    fun notADue() {
        assertNull(SmsParser.parseDue("X", "Rs 250 debited from A/c 1234 at Swiggy", today))
    }
}

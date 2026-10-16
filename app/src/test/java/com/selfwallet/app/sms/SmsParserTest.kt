package com.selfwallet.app.sms

import com.selfwallet.app.sms.SmsParser.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    private fun parse(body: String, sender: String = "AD-HDFCBK") = SmsParser.parse(sender, body)

    @Test
    fun indianUpiDebit() {
        val r = parse("Rs.250.00 debited from A/c XX1234 on 12-10-24 to VPA swiggy@icici. Avl Bal Rs.10,234.50")
        assertNotNull(r); r!!
        assertEquals(250.0, r.amount, 0.001)
        assertEquals("INR", r.currency)
        assertEquals(Kind.EXPENSE, r.kind)
        assertEquals("Swiggy", r.merchant)
        assertEquals("1234", r.account)
    }

    @Test
    fun uaeCardSpendSkipsLimit() {
        val r = parse("AED 45.50 spent on your Emirates NBD Credit Card ending 5678 at CARREFOUR DUBAI on 16/10/2026. Avl limit AED 12,000.00")!!
        assertEquals(45.5, r.amount, 0.001)
        assertEquals("AED", r.currency)
        assertEquals(Kind.EXPENSE, r.kind)
        assertEquals("Carrefour Dubai", r.merchant)
        assertEquals("5678", r.account)
    }

    @Test
    fun salaryCredit() {
        val r = parse("Your A/c 1234 is credited with INR 50,000.00 on 01-10 by NEFT from ACME CORP")!!
        assertEquals(50000.0, r.amount, 0.001)
        assertEquals(Kind.INCOME, r.kind)
        assertEquals("Acme Corp", r.merchant)
    }

    @Test
    fun usdPurchase() {
        val r = parse("Purchase of USD 12.99 at NETFLIX.COM with card ***9876", sender = "CITI")!!
        assertEquals(12.99, r.amount, 0.001)
        assertEquals("USD", r.currency)
        assertEquals("NETFLIX.COM", r.merchant)
    }

    @Test
    fun symbolCurrencies() {
        assertEquals("GBP", parse("You spent £23.40 at TESCO STORES")!!.currency)
        assertEquals("EUR", parse("€9.99 charged at SPOTIFY on your card 1111")!!.currency)
        assertNull(parse("Card payment of €9.99 declined at SPOTIFY"))
        assertEquals("INR", parse("₹1,499 paid to Amazon via UPI")!!.currency)
        assertEquals(1499.0, parse("₹1,499 paid to Amazon via UPI")!!.amount, 0.001)
    }

    @Test
    fun numberBeforeCurrency() {
        val r = parse("150.000 KWD withdrawn from ATM at SALMIYA BRANCH")!!
        assertEquals(150.0, r.amount, 0.001)
        assertEquals("KWD", r.currency)
    }

    @Test
    fun ignoresOtp() {
        assertNull(parse("123456 is your OTP for txn of Rs 500 at Amazon. Do not share it with anyone."))
        assertNull(parse("OTP for transaction of INR 2,000.00 at FLIPKART is 438221"))
    }

    @Test
    fun ignoresPromotionsAndReminders() {
        assertNull(parse("Dear Customer, you have a pre-approved loan of Rs 5,00,000. Apply now"))
        assertNull(parse("Your credit card bill of Rs 4,500 is due on 05-11. Pay now to avoid charges"))
        assertNull(parse("Rs 300 will be debited from your account for autopay on 20-10"))
        assertNull(parse("Hi! Lunch at 1?", sender = "+971501234567"))
    }

    @Test
    fun refundIsIncome() {
        val r = parse("Refund of Rs 799.00 from MYNTRA has been credited to your a/c XX9911")!!
        assertEquals(Kind.INCOME, r.kind)
        assertEquals(799.0, r.amount, 0.001)
    }

    @Test
    fun fallsBackToSender() {
        val r = parse("SAR 120 debited from your account 4455 on 15/10", sender = "AlRajhiBank")!!
        assertEquals("SAR", r.currency)
        assertEquals("AlRajhiBank", r.merchant)
    }
}

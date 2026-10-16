package com.selfwallet.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceParserTest {
    private val cats = listOf("Food & Dining", "Transport", "Hotel & Stay")

    @Test
    fun lunchCorporate() {
        val r = VoiceParser.parse("Lunch 45 dirhams corporate", cats)
        assertEquals(45.0, r.amount!!, 0.001)
        assertEquals("AED", r.currency)
        assertEquals(true, r.corporate)
        assertEquals("Lunch", r.merchant)
    }

    @Test
    fun taxi() {
        val r = VoiceParser.parse("spent 120 rupees on transport uber", cats)
        assertEquals(120.0, r.amount!!, 0.001)
        assertEquals("INR", r.currency)
        assertEquals("Transport", r.categoryName)
    }
}
